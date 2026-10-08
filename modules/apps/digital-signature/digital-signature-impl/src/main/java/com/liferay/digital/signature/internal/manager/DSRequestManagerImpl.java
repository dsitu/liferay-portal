/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.internal.manager;

import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureConfigurationUtil;
import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.constants.DSRequestRecipientConstants;
import com.liferay.digital.signature.manager.DSEnvelopeManager;
import com.liferay.digital.signature.manager.DSRequestManager;
import com.liferay.digital.signature.model.DSDocument;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.model.DSRequestRecipient;
import com.liferay.digital.signature.url.SignDSURLProvider;
import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.mail.kernel.model.MailMessage;
import com.liferay.mail.kernel.service.MailService;
import com.liferay.object.constants.ObjectDefinitionConstants;
import com.liferay.object.constants.ObjectEntryFolderConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.rest.filter.factory.FilterFactory;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.function.transform.TransformUtil;
import com.liferay.petra.sql.dsl.expression.Predicate;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.NoSuchUserException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.UserConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.Sort;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermissionRegistryUtil;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.Base64;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PrefsPropsUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import jakarta.mail.internet.InternetAddress;

import java.io.Serializable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Brian I. Kim
 */
@Component(service = DSRequestManager.class)
public class DSRequestManagerImpl implements DSRequestManager {

	@Override
	public DSRequest addDSRequest(
			long companyId, long groupId, long userId, DSEnvelope dsEnvelope,
			long[] fileEntryIds)
		throws Exception {

		if (!_isEnabled(companyId, groupId)) {
			throw new PortalException(
				"Digital signatures are not enabled for group " + groupId);
		}

		if (ArrayUtil.isEmpty(fileEntryIds)) {
			throw new PortalException(
				"A signature request must have at least one document");
		}

		int expireAfter = dsEnvelope.getExpireAfter();

		if ((expireAfter > 0) && (dsEnvelope.getExpireWarn() >= expireAfter)) {
			throw new PortalException(
				"Days to warn signers must be fewer than days until " +
					"expiration");
		}

		Map<Long, DSRequest> dsRequests = getDSRequests(
			companyId, ListUtil.fromArray(fileEntryIds));

		for (Map.Entry<Long, DSRequest> entry : dsRequests.entrySet()) {
			DSRequest dsRequest = entry.getValue();

			if (!dsRequest.isRequestable()) {
				throw new PortalException(
					StringBundler.concat(
						"File entry ", entry.getKey(),
						" already has a signature request with status \"",
						dsRequest.getStatus(), "\""));
			}
		}

		User user = _userLocalService.getUser(userId);

		dsEnvelope.setDSDocuments(_getDSDocuments(fileEntryIds));
		dsEnvelope.setSenderEmailAddress(user.getEmailAddress());
		dsEnvelope.setStatus(DSRequestConstants.STATUS_SENT);

		for (DSRecipient dsRecipient : dsEnvelope.getDSRecipients()) {
			User recipientUser = _userLocalService.fetchUserByEmailAddress(
				companyId, dsRecipient.getEmailAddress());

			if (recipientUser != null) {
				dsRecipient.setDSClientUserId(
					String.valueOf(recipientUser.getUserId()));
				dsRecipient.setName(recipientUser.getFullName());
			}
		}

		DSEnvelope sentDSEnvelope = _dsEnvelopeManager.addDSEnvelope(
			companyId, groupId, dsEnvelope);

		return _addDSRequest(
			companyId, groupId, userId, sentDSEnvelope, fileEntryIds);
	}

	@Override
	public DSRequest fetchDSRequest(long dsRequestId) {
		ObjectEntry dsRequestObjectEntry =
			_objectEntryLocalService.fetchObjectEntry(dsRequestId);

		if (dsRequestObjectEntry == null) {
			return null;
		}

		long companyId = dsRequestObjectEntry.getCompanyId();

		if (!_isEnabled(companyId, 0)) {
			return null;
		}

		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);

		if ((dsRequestObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null) ||
			(dsRequestObjectEntry.getObjectDefinitionId() !=
				dsRequestObjectDefinition.getObjectDefinitionId())) {

			return null;
		}

		try {
			Map<Long, DSRequest> dsRequestsMap = _getDSRequestsMap(
				companyId, Collections.singleton(dsRequestId),
				dsRequestRecipientObjectDefinition);

			return dsRequestsMap.get(dsRequestId);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to load the signature request " + dsRequestId,
				exception);

			return null;
		}
	}

	@Override
	public DSRequest fetchDSRequest(long companyId, long fileEntryId) {
		Map<Long, DSRequest> dsRequests = getDSRequests(
			companyId, Collections.singleton(fileEntryId));

		return dsRequests.get(fileEntryId);
	}

	@Override
	public Map<Long, DSRequest> getDSRequests(
		long companyId, Collection<Long> fileEntryIds) {

		Map<Long, DSRequest> dsRequests = new HashMap<>();

		if (!_isEnabled(companyId, 0) || (fileEntryIds == null) ||
			fileEntryIds.isEmpty()) {

			return dsRequests;
		}

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_DOCUMENT", companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);

		if ((dsRequestDocumentObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null)) {

			return dsRequests;
		}

		try {
			Map<Long, Long> dsRequestIdsByFileEntryId =
				_getDSRequestIdsByFileEntryId(
					companyId, dsRequestDocumentObjectDefinition, fileEntryIds);

			if (dsRequestIdsByFileEntryId.isEmpty()) {
				return dsRequests;
			}

			Map<Long, DSRequest> dsRequestsMap = _getDSRequestsMap(
				companyId, new HashSet<>(dsRequestIdsByFileEntryId.values()),
				dsRequestRecipientObjectDefinition);

			for (Map.Entry<Long, Long> entry :
					dsRequestIdsByFileEntryId.entrySet()) {

				DSRequest dsRequest = dsRequestsMap.get(entry.getValue());

				if (dsRequest != null) {
					dsRequests.put(entry.getKey(), dsRequest);
				}
			}
		}
		catch (Exception exception) {
			_log.error(
				"Unable to load the signature requests for company " +
					companyId,
				exception);
		}

		return dsRequests;
	}

	@Override
	public List<DSRequest> getFileEntryDSRequests(
		long companyId, long fileEntryId) {

		List<DSRequest> dsRequests = new ArrayList<>();

		if (!_isEnabled(companyId, 0)) {
			return dsRequests;
		}

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_DOCUMENT", companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);

		if ((dsRequestDocumentObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null)) {

			return dsRequests;
		}

		try {
			Set<Long> dsRequestIds = new LinkedHashSet<>();

			for (Map<String, Serializable> documentValues :
					_getValuesList(
						companyId, "(fileEntryId eq " + fileEntryId + ")",
						dsRequestDocumentObjectDefinition,
						new Sort[] {
							new Sort(Field.CREATE_DATE, Sort.LONG_TYPE, true)
						})) {

				dsRequestIds.add(
					GetterUtil.getLong(
						documentValues.get(
							"r_dsRequestToDSRequestDocuments_l_dsRequestId")));
			}

			if (dsRequestIds.isEmpty()) {
				return dsRequests;
			}

			Map<Long, DSRequest> dsRequestsMap = _getDSRequestsMap(
				companyId, dsRequestIds, dsRequestRecipientObjectDefinition);

			for (long dsRequestId : dsRequestIds) {
				DSRequest dsRequest = dsRequestsMap.get(dsRequestId);

				if (dsRequest != null) {
					dsRequests.add(dsRequest);
				}
			}
		}
		catch (Exception exception) {
			_log.error(
				"Unable to load the signature requests for file entry " +
					fileEntryId,
				exception);
		}

		return dsRequests;
	}

	@Override
	public List<DSRequest> getRecipientDSRequests(
		long companyId, long userId, String search, int start, int end) {

		try {
			return _getDSRequests(
				companyId, _getRecipientFilterString(companyId, userId), search,
				start, end, false);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to load the signature requests for user " + userId,
				exception);

			return new ArrayList<>();
		}
	}

	@Override
	public int getRecipientDSRequestsCount(
		long companyId, long userId, String search) {

		try {
			return _getDSRequestsCount(
				companyId, _getRecipientFilterString(companyId, userId), search,
				false);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to count the signature requests for user " + userId,
				exception);

			return 0;
		}
	}

	@Override
	public List<DSRequest> getSiteDSRequests(
		long companyId, long siteGroupId, String search, int start, int end) {

		try {
			return _getDSRequests(
				companyId, "siteGroupId eq " + siteGroupId, search, start, end,
				true);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to load the signature requests for site " + siteGroupId,
				exception);

			return new ArrayList<>();
		}
	}

	@Override
	public int getSiteDSRequestsCount(
		long companyId, long siteGroupId, String search) {

		try {
			return _getDSRequestsCount(
				companyId, "siteGroupId eq " + siteGroupId, search, true);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to count the signature requests for site " +
					siteGroupId,
				exception);

			return 0;
		}
	}

	@Override
	public boolean hasPermission(
			PermissionChecker permissionChecker, DSRequest dsRequest,
			String actionId)
		throws PortalException {

		User user = permissionChecker.getUser();

		if (dsRequest.getRequesterUserId() == user.getUserId()) {
			return true;
		}

		if (Objects.equals(actionId, ActionKeys.VIEW)) {
			for (DSRequestRecipient dsRequestRecipient :
					dsRequest.getDSRequestRecipients()) {

				if ((dsRequestRecipient.getUserId() == user.getUserId()) ||
					StringUtil.equalsIgnoreCase(
						dsRequestRecipient.getEmailAddress(),
						user.getEmailAddress())) {

					return true;
				}
			}
		}

		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", dsRequest.getCompanyId());

		if (dsRequestObjectDefinition == null) {
			return false;
		}

		ModelResourcePermission<?> modelResourcePermission =
			ModelResourcePermissionRegistryUtil.getModelResourcePermission(
				dsRequestObjectDefinition.getClassName());

		return modelResourcePermission.contains(
			permissionChecker, dsRequest.getDSRequestId(), actionId);
	}

	@Override
	public void sendDSRequestNotifications(
		long companyId, long groupId, DSRequest dsRequest) {

		_sendDSRequestNotifications(companyId, groupId, dsRequest);
	}

	@Override
	public int sendSignatureReminders(long companyId) {
		if (!_isEnabled(companyId, 0)) {
			return 0;
		}

		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				companyId, 0);

		if (!digitalSignatureConfiguration.signatureReminderEnabled()) {
			return 0;
		}

		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);

		if (dsRequestRecipientObjectDefinition == null) {
			return 0;
		}

		int count = 0;

		try {
			Set<Long> dsRequestIds = new HashSet<>();

			for (Map<String, Serializable> recipientValues :
					_getValuesList(
						companyId,
						StringBundler.concat(
							"(requestRecipientStatus eq '",
							DSRequestRecipientConstants.STATUS_SENT, "')"),
						dsRequestRecipientObjectDefinition, null)) {

				dsRequestIds.add(
					GetterUtil.getLong(
						recipientValues.get(
							"r_dsRequestToDSRequestRecipients_l_dsRequestId")));
			}

			if (dsRequestIds.isEmpty()) {
				return 0;
			}

			Map<Long, DSRequest> dsRequestsMap = _getDSRequestsMap(
				companyId, dsRequestIds, dsRequestRecipientObjectDefinition);

			for (DSRequest dsRequest : dsRequestsMap.values()) {
				if (!dsRequest.isTerminal()) {
					count += _sendDSRequestNotifications(
						companyId, dsRequest.getSiteGroupId(), dsRequest);
				}
			}
		}
		catch (Exception exception) {
			_log.error(
				"Unable to send signature reminders for company " + companyId,
				exception);
		}

		return count;
	}

	@Override
	public void updateDSRequest(
		long companyId, long groupId, String providerRequestId) {

		if (!_isEnabled(companyId, groupId) ||
			Validator.isNull(providerRequestId)) {

			return;
		}

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_DOCUMENT", companyId);
		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);

		if ((dsRequestDocumentObjectDefinition == null) ||
			(dsRequestObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null)) {

			return;
		}

		try {
			DSEnvelope dsEnvelope = _dsEnvelopeManager.getDSEnvelope(
				companyId, groupId, providerRequestId);

			for (DSRecipient dsRecipient : dsEnvelope.getDSRecipients()) {
				dsRecipientsMap.put(
					dsRecipient.getDSRecipientId(), dsRecipient);
			}

			for (Map<String, Serializable> requestValues :
					_getValuesList(
						companyId,
						"(providerRequestId eq '" + providerRequestId + "')",
						dsRequestObjectDefinition, null)) {

				long dsRequestId = MapUtil.getLong(
					requestValues,
					dsRequestObjectDefinition.getPKObjectFieldName());

				_updateRequestStatus(
					companyId, groupId, dsEnvelope, dsRequestId);

				List<DSRequestRecipient> sentDSRequestRecipients =
					_updateRecipientStatuses(
						companyId, groupId, dsRecipientsMap,
						dsRequestId, dsRequestRecipientObjectDefinition);

				if (!sentDSRequestRecipients.isEmpty()) {
					_sendDSRequestNotifications(
						fetchDSRequest(dsRequestId), sentDSRequestRecipients);
				}

				if (Objects.equals(
						_getRequestStatus(dsEnvelope),
						DSRequestConstants.STATUS_COMPLETED) &&
					!Objects.equals(
						GetterUtil.getString(
							requestValues.get("requestStatus")),
						DSRequestConstants.STATUS_COMPLETED)) {

					_archiveSignedDocument(
						companyId, groupId, dsEnvelope,
						dsRequestDocumentObjectDefinition, dsRequestId);
				}
			}
		}
		catch (Exception exception) {
			_log.error(
				"Unable to update the signature request for envelope " +
					providerRequestId,
				exception);
		}
	}

	@Override
	public void voidDSRequest(
		long companyId, long groupId, DSRequest dsRequest, String reason) {

		if (!_isEnabled(companyId, groupId) || (dsRequest == null)) {
			return;
		}

		String providerRequestId = dsRequest.getProviderRequestId();

		if (Validator.isNull(providerRequestId)) {
			return;
		}

		_dsEnvelopeManager.voidDSEnvelope(
			companyId, groupId, providerRequestId, reason);

		updateDSRequest(companyId, groupId, providerRequestId);
	}

	private DSRequest _addDSRequest(
			long companyId, long groupId, long userId, DSEnvelope dsEnvelope,
			long[] fileEntryIds)
		throws Exception {

		ObjectEntry dsRequestObjectEntry = null;
		List<DSRequestRecipient> dsRequestRecipients = new ArrayList<>();

		try {
			ObjectDefinition dsRequestDocumentObjectDefinition =
				_objectDefinitionLocalService.
					fetchObjectDefinitionByExternalReferenceCode(
						"L_DS_REQUEST_DOCUMENT", companyId);
			ObjectDefinition dsRequestObjectDefinition =
				_objectDefinitionLocalService.
					fetchObjectDefinitionByExternalReferenceCode(
						"L_DS_REQUEST", companyId);
			ObjectDefinition dsRequestRecipientObjectDefinition =
				_objectDefinitionLocalService.
					fetchObjectDefinitionByExternalReferenceCode(
						"L_DS_REQUEST_RECIPIENT", companyId);

			if ((dsRequestDocumentObjectDefinition == null) ||
				(dsRequestObjectDefinition == null) ||
				(dsRequestRecipientObjectDefinition == null)) {

				throw new PortalException(
					"Unable to find the signature request object definitions");
			}

			ServiceContext serviceContext = _getServiceContext(
				companyId, groupId, userId);

			dsRequestObjectEntry = _objectEntryLocalService.addObjectEntry(
				0, userId, dsRequestObjectDefinition.getObjectDefinitionId(),
				ObjectEntryFolderConstants.
					PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
				null,
				HashMapBuilder.<String, Serializable>put(
					"emailBody", dsEnvelope.getEmailBlurb()
				).put(
					"emailSubject", dsEnvelope.getEmailSubject()
				).put(
					"providerKey", "docusign"
				).put(
					"providerRequestId", dsEnvelope.getDSEnvelopeId()
				).put(
					"requestExpirationDate",
					() -> _toDate(dsEnvelope.getExpireLocalDateTime())
				).put(
					"requestStatus", _getRequestStatus(dsEnvelope)
				).put(
					"siteGroupId", _getSiteGroupId(groupId)
				).build(),
				serviceContext);

			for (long fileEntryId : fileEntryIds) {
				_objectEntryLocalService.addObjectEntry(
					0, userId,
					dsRequestDocumentObjectDefinition.getObjectDefinitionId(),
					ObjectEntryFolderConstants.
						PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
					null,
					HashMapBuilder.<String, Serializable>put(
						"fileEntryId", fileEntryId
					).put(
						"r_dsRequestToDSRequestDocuments_l_dsRequestId",
						dsRequestObjectEntry.getObjectEntryId()
					).build(),
					serviceContext);
			}

			for (DSRecipient dsRecipient : dsEnvelope.getDSRecipients()) {
				ObjectEntry dsRequestRecipientObjectEntry =
					_objectEntryLocalService.addObjectEntry(
						0, userId,
						dsRequestRecipientObjectDefinition.
							getObjectDefinitionId(),
						ObjectEntryFolderConstants.
							PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
						null,
						HashMapBuilder.<String, Serializable>put(
							"emailAddress", dsRecipient.getEmailAddress()
						).put(
							"name", dsRecipient.getName()
						).put(
							"providerRecipientId",
							dsRecipient.getDSRecipientId()
						).put(
							"r_dsRequestToDSRequestRecipients_l_dsRequestId",
							dsRequestObjectEntry.getObjectEntryId()
						).put(
							"r_userToDSRequestRecipients_userId",
							_getRecipientUserId(
								companyId, dsRecipient.getEmailAddress())
						).put(
							"requestRecipientStatus",
							_getRequestRecipientStatus(dsRecipient)
						).put(
							"sentDate",
							() -> _toDate(dsRecipient.getSentLocalDateTime())
						).put(
							"signingOrder",
							Math.max(1, dsRecipient.getRoutingOrder())
						).build(),
						serviceContext);

				dsRequestRecipients.add(
					new DSRequestRecipient(
						dsRequestRecipientObjectEntry.getValues()));
			}
		}
		catch (Exception exception1) {
			if (dsRequestObjectEntry != null) {
				_objectEntryLocalService.deleteObjectEntry(
					dsRequestObjectEntry);
			}

			try {
				_dsEnvelopeManager.voidDSEnvelope(
					companyId, groupId, dsEnvelope.getDSEnvelopeId(),
					"Unable to record the signature request");
			}
			catch (Exception exception2) {
				exception1.addSuppressed(exception2);
			}

			throw exception1;
		}

		return new DSRequest(
			dsRequestObjectEntry.getCompanyId(),
			dsRequestObjectEntry.getCreateDate(),
			dsRequestObjectEntry.getObjectEntryId(), dsRequestRecipients,
			ListUtil.fromArray(fileEntryIds),
			_getRequesterEmailAddress(dsRequestObjectEntry),
			_getRequesterName(dsRequestObjectEntry),
			dsRequestObjectEntry.getUserId(), dsRequestObjectEntry.getValues());
	}

	private void _archiveSignedDocument(
			long companyId, long groupId, DSEnvelope dsEnvelope,
			ObjectDefinition dsRequestDocumentObjectDefinition,
			long dsRequestId)
		throws Exception {

		List<Long> fileEntryIds = _getDSRequestFileEntryIds(
			companyId, dsRequestDocumentObjectDefinition, dsRequestId);

		if (fileEntryIds.isEmpty()) {
			return;
		}

		ObjectEntry dsRequestObjectEntry =
			_objectEntryLocalService.fetchObjectEntry(dsRequestId);

		if (dsRequestObjectEntry == null) {
			return;
		}

		long userId = dsRequestObjectEntry.getUserId();

		for (long fileEntryId : fileEntryIds) {
			byte[] bytes = _dsEnvelopeManager.getSignedDocument(
				companyId, groupId, dsEnvelope.getDSEnvelopeId(),
				String.valueOf(fileEntryId));

			if (ArrayUtil.isEmpty(bytes)) {
				continue;
			}

			FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);

			_dlAppLocalService.updateFileEntry(
				userId, fileEntryId, fileEntry.getFileName(),
				ContentTypes.APPLICATION_PDF, fileEntry.getTitle(), null, null,
				null, DLVersionNumberIncrease.MAJOR, bytes, null, null, null,
				_getServiceContext(companyId, fileEntry.getGroupId(), userId));
		}
	}

	private List<DSDocument> _getDSDocuments(long[] fileEntryIds)
		throws Exception {

		return TransformUtil.transformToList(
			fileEntryIds, fileEntryId -> _toDSDocument(fileEntryId));
	}

	private List<Long> _getDSRequestFileEntryIds(
			long companyId, ObjectDefinition dsRequestDocumentObjectDefinition,
			long dsRequestId)
		throws Exception {

		return TransformUtil.transform(
			_getValuesList(
				companyId,
				StringBundler.concat(
					"(r_dsRequestToDSRequestDocuments_l_dsRequestId eq '",
					dsRequestId, "')"),
				dsRequestDocumentObjectDefinition, null),
			documentValues -> GetterUtil.getLong(
				documentValues.get("fileEntryId")));
	}

	private Map<Long, Long> _getDSRequestIdsByFileEntryId(
			long companyId, ObjectDefinition dsRequestDocumentObjectDefinition,
			Collection<Long> fileEntryIds)
		throws Exception {

		Map<Long, Long> dsRequestIdsByFileEntryId = new HashMap<>();

		for (Map<String, Serializable> documentValues :
				_getValuesList(
					companyId,
					StringBundler.concat(
						"(fileEntryId in (",
						StringUtil.merge(fileEntryIds, ", "), "))"),
					dsRequestDocumentObjectDefinition,
					new Sort[] {
						new Sort(Field.CREATE_DATE, Sort.LONG_TYPE, true)
					})) {

			dsRequestIdsByFileEntryId.putIfAbsent(
				MapUtil.getLong(documentValues, "fileEntryId"),
				GetterUtil.getLong(
					documentValues.get(
						"r_dsRequestToDSRequestDocuments_l_dsRequestId")));
		}

		return dsRequestIdsByFileEntryId;
	}

	private List<DSRequest> _getDSRequests(
			long companyId, String filterString, String search, int start,
			int end, boolean checkPermissions)
		throws Exception {

		List<DSRequest> dsRequests = new ArrayList<>();

		if (!_isEnabled(companyId, 0)) {
			return dsRequests;
		}

		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);

		if ((dsRequestObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null)) {

			return dsRequests;
		}

		Set<Long> dsRequestIds = new LinkedHashSet<>();

		for (Map<String, Serializable> requestValues :
				_getValuesList(
					companyId, dsRequestObjectDefinition, filterString, search,
					start, end,
					new Sort[] {
						new Sort(Field.CREATE_DATE, Sort.LONG_TYPE, true)
					},
					checkPermissions)) {

			dsRequestIds.add(
				GetterUtil.getLong(
					requestValues.get(
						dsRequestObjectDefinition.getPKObjectFieldName())));
		}

		if (dsRequestIds.isEmpty()) {
			return dsRequests;
		}

		Map<Long, DSRequest> dsRequestsMap = _getDSRequestsMap(
			companyId, dsRequestIds, dsRequestRecipientObjectDefinition);

		for (long dsRequestId : dsRequestIds) {
			DSRequest dsRequest = dsRequestsMap.get(dsRequestId);

			if (dsRequest != null) {
				dsRequests.add(dsRequest);
			}
		}

		return dsRequests;
	}

	private Map<Long, DSRequest> _getDSRequestsMap(
			long companyId, Set<Long> dsRequestIds,
			ObjectDefinition dsRequestRecipientObjectDefinition)
		throws Exception {

		Map<Long, DSRequest> dsRequestsMap = new HashMap<>();

		Map<Long, List<DSRequestRecipient>> dsRequestRecipientsMap =
			new HashMap<>();

		for (Map<String, Serializable> recipientValues :
				_getValuesList(
					companyId,
					StringBundler.concat(
						"(r_dsRequestToDSRequestRecipients_l_dsRequestId in ('",
						StringUtil.merge(dsRequestIds, "', '"), "'))"),
					dsRequestRecipientObjectDefinition, null)) {

			List<DSRequestRecipient> dsRequestRecipients =
				dsRequestRecipientsMap.computeIfAbsent(
					GetterUtil.getLong(
						recipientValues.get(
							"r_dsRequestToDSRequestRecipients_l_dsRequestId")),
					dsRequestId -> new ArrayList<>());

			dsRequestRecipients.add(new DSRequestRecipient(recipientValues));
		}

		for (List<DSRequestRecipient> dsRequestRecipients :
				dsRequestRecipientsMap.values()) {

			dsRequestRecipients.sort(
				Comparator.comparingInt(DSRequestRecipient::getSigningOrder));
		}

		Map<Long, List<Long>> fileEntryIdsMap = _getFileEntryIdsMap(
			companyId, dsRequestIds);

		for (long dsRequestId : dsRequestIds) {
			ObjectEntry dsRequestObjectEntry =
				_objectEntryLocalService.fetchObjectEntry(dsRequestId);

			if (dsRequestObjectEntry == null) {
				continue;
			}

			dsRequestsMap.put(
				dsRequestId,
				new DSRequest(
					dsRequestObjectEntry.getCompanyId(),
					dsRequestObjectEntry.getCreateDate(), dsRequestId,
					dsRequestRecipientsMap.getOrDefault(
						dsRequestId, Collections.emptyList()),
					fileEntryIdsMap.getOrDefault(
						dsRequestId, Collections.emptyList()),
					_getRequesterEmailAddress(dsRequestObjectEntry),
					_getRequesterName(dsRequestObjectEntry),
					dsRequestObjectEntry.getUserId(),
					dsRequestObjectEntry.getValues()));
		}

		return dsRequestsMap;
	}

	private int _getDSRequestsCount(
			long companyId, String filterString, String search,
			boolean checkPermissions)
		throws Exception {

		if (!_isEnabled(companyId, 0)) {
			return 0;
		}

		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);

		if (dsRequestObjectDefinition == null) {
			return 0;
		}

		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		try {
			if (!checkPermissions) {
				PermissionThreadLocal.setPermissionChecker(null);
			}

			return _objectEntryLocalService.getValuesListCount(
				new Long[] {0L}, companyId,
				dsRequestObjectDefinition.getUserId(),
				dsRequestObjectDefinition.getObjectDefinitionId(),
				_filterFactory.create(filterString, dsRequestObjectDefinition),
				false, search);
		}
		finally {
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
		}
	}

	private String _getEmailBody(
		String emailMessage, Locale locale, String url) {

		String message = emailMessage;

		if (Validator.isNull(message)) {
			message = _language.get(locale, "you-have-a-document-to-sign");
		}
		else {
			message = HtmlUtil.escape(message);
		}

		return StringBundler.concat(
			"<p>", message, "</p><p><a href=\"", url, "\">",
			_language.get(locale, "review-and-sign"), "</a></p>");
	}

	private Map<Long, List<Long>> _getFileEntryIdsMap(
			long companyId, Set<Long> dsRequestIds)
		throws Exception {

		Map<Long, List<Long>> fileEntryIdsMap = new HashMap<>();

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_DOCUMENT", companyId);

		if (dsRequestDocumentObjectDefinition == null) {
			return fileEntryIdsMap;
		}

		for (Map<String, Serializable> documentValues :
				_getValuesList(
					companyId,
					StringBundler.concat(
						"(r_dsRequestToDSRequestDocuments_l_dsRequestId in ('",
						StringUtil.merge(dsRequestIds, "', '"), "'))"),
					dsRequestDocumentObjectDefinition, null)) {

			List<Long> fileEntryIds = fileEntryIdsMap.computeIfAbsent(
				GetterUtil.getLong(
					documentValues.get(
						"r_dsRequestToDSRequestDocuments_l_dsRequestId")),
				dsRequestId -> new ArrayList<>());

			fileEntryIds.add(MapUtil.getLong(documentValues, "fileEntryId"));
		}

		return fileEntryIdsMap;
	}

	private Locale _getLocale(long userId, long siteGroupId) throws Exception {
		User user = _userLocalService.fetchUser(userId);

		if (user != null) {
			return user.getLocale();
		}

		return _portal.getSiteDefaultLocale(siteGroupId);
	}

	private String _getLoginURL(String url) {
		String path = HttpComponentsUtil.getPath(url);

		return HttpComponentsUtil.addParameter(
			StringBundler.concat(
				url.substring(0, url.length() - path.length()),
				_portal.getPathMain(), "/portal/login"),
			"redirect", path);
	}

	private String _getRecipientFilterString(long companyId, long userId)
		throws PortalException {

		User user = _userLocalService.getUser(userId);

		if (user.getCompanyId() != companyId) {
			throw new NoSuchUserException(
				"No user exists with the primary key " + userId);
		}

		String emailAddressFieldName =
			_RECIPIENTS_RELATIONSHIP_PATH + "emailAddress";
		String userIdFieldName =
			_RECIPIENTS_RELATIONSHIP_PATH +
				"r_userToDSRequestRecipients_userId";

		return StringBundler.concat(
			"(", userIdFieldName, " eq '", userId, "') or (", userIdFieldName,
			" eq '0' and ", emailAddressFieldName, " eq '",
			StringUtil.replace(user.getEmailAddress(), '\'', "''"), "')");
	}

	private long _getRecipientUserId(long companyId, String emailAddress) {
		if (Validator.isNull(emailAddress)) {
			return 0;
		}

		User user = _userLocalService.fetchUserByEmailAddress(
			companyId, emailAddress);

		if (user == null) {
			return 0;
		}

		return user.getUserId();
	}

	private String _getRequestRecipientStatus(DSRecipient dsRecipient) {
		String status = StringUtil.toLowerCase(dsRecipient.getStatus());

		if (ArrayUtil.contains(DSRequestRecipientConstants.STATUSES, status)) {
			return status;
		}

		return DSRequestRecipientConstants.STATUS_SENT;
	}

	private String _getRequestStatus(DSEnvelope dsEnvelope) {
		LocalDateTime expireLocalDateTime = dsEnvelope.getExpireLocalDateTime();
		String status = StringUtil.toLowerCase(dsEnvelope.getStatus());
		LocalDateTime statusChangedLocalDateTime =
			dsEnvelope.getStatusChangedLocalDateTime();

		if (Objects.equals(status, DSRequestConstants.STATUS_VOIDED) &&
			(expireLocalDateTime != null) &&
			(statusChangedLocalDateTime != null) &&
			!statusChangedLocalDateTime.isBefore(expireLocalDateTime)) {

			return DSRequestConstants.STATUS_EXPIRED;
		}

		if (ArrayUtil.contains(DSRequestConstants.STATUSES, status)) {
			return status;
		}

		return DSRequestConstants.STATUS_SENT;
	}

	private String _getRequesterEmailAddress(ObjectEntry objectEntry) {
		User user = _userLocalService.fetchUser(objectEntry.getUserId());

		if (user == null) {
			return null;
		}

		if (_isServiceAccount(user)) {
			return PrefsPropsUtil.getString(
				user.getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);
		}

		return user.getEmailAddress();
	}

	private String _getRequesterName(ObjectEntry objectEntry) {
		User user = _userLocalService.fetchUser(objectEntry.getUserId());

		if (user == null) {
			return objectEntry.getUserName();
		}

		if (_isServiceAccount(user)) {
			Company company = _companyLocalService.fetchCompany(
				user.getCompanyId());

			if (company != null) {
				return company.getName();
			}
		}

		return user.getFullName();
	}

	private ServiceContext _getServiceContext(
		long companyId, long groupId, long userId) {

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setCompanyId(companyId);
		serviceContext.setScopeGroupId(groupId);
		serviceContext.setUserId(userId);

		return serviceContext;
	}

	private long _getSiteGroupId(long groupId) {
		Group group = _groupLocalService.fetchGroup(groupId);

		if ((group == null) || !group.isSite()) {
			return 0;
		}

		return groupId;
	}

	private List<Map<String, Serializable>> _getValuesList(
			long companyId, ObjectDefinition objectDefinition,
			String filterString, String search, int start, int end,
			Sort[] sorts, boolean checkPermissions)
		throws Exception {

		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		try {
			if (!checkPermissions) {
				PermissionThreadLocal.setPermissionChecker(null);
			}

			return _objectEntryLocalService.getValuesList(
				0, companyId, objectDefinition.getUserId(),
				objectDefinition.getObjectDefinitionId(),
				_filterFactory.create(filterString, objectDefinition), search,
				start, end, sorts);
		}
		finally {
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
		}
	}

	private List<Map<String, Serializable>> _getValuesList(
			long companyId, String filterString,
			ObjectDefinition objectDefinition, Sort[] sorts)
		throws Exception {

		return _getValuesList(
			companyId, objectDefinition, filterString, null, QueryUtil.ALL_POS,
			QueryUtil.ALL_POS, sorts, false);
	}

	private boolean _isEnabled(long companyId, long groupId) {
		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				companyId, groupId);

		if (digitalSignatureConfiguration.enabled() &&
			digitalSignatureConfiguration.enableEmbeddedView()) {

			return true;
		}

		return false;
	}

	private boolean _isServiceAccount(User user) {
		if ((user.getType() == UserConstants.TYPE_DEFAULT_SERVICE_ACCOUNT) ||
			(user.getType() == UserConstants.TYPE_SERVICE_ACCOUNT)) {

			return true;
		}

		return false;
	}

	private void _sendDSRequestNotification(
		DSRequest dsRequest, DSRequestRecipient dsRequestRecipient) {

		String emailAddress = dsRequestRecipient.getEmailAddress();

		if (!Validator.isEmailAddress(emailAddress)) {
			return;
		}

		try {
			Locale locale = _getLocale(
				dsRequestRecipient.getUserId(), dsRequest.getSiteGroupId());

			String subject = dsRequest.getEmailSubject();

			if (Validator.isNull(subject)) {
				subject = _language.get(locale, "you-have-a-document-to-sign");
			}

			long companyId = dsRequest.getCompanyId();

			String fromAddress = PrefsPropsUtil.getString(
				companyId, PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);
			String fromName = PrefsPropsUtil.getString(
				companyId, PropsKeys.ADMIN_EMAIL_FROM_NAME);
			String url = _getLoginURL(
				_signDSURLProvider.getURL(
					companyId, dsRequest.getSiteGroupId(),
					dsRequest.getDSRequestId()));

			MailMessage mailMessage = new MailMessage(
				new InternetAddress(fromAddress, fromName),
				new InternetAddress(emailAddress), subject,
				_getEmailBody(dsRequest.getEmailBody(), locale, url), true);

			_mailService.sendEmail(mailMessage);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to send sign email for signature request " +
					dsRequest.getDSRequestId(),
				exception);
		}
	}

	private int _sendDSRequestNotifications(
		DSRequest dsRequest, List<DSRequestRecipient> dsRequestRecipients) {

		if ((dsRequest == null) ||
			Validator.isNull(dsRequest.getProviderRequestId())) {

			return 0;
		}

		int count = 0;

		for (DSRequestRecipient dsRequestRecipient : dsRequestRecipients) {
			if ((dsRequestRecipient.getUserId() <= 0) ||
				Validator.isNull(dsRequestRecipient.getEmailAddress())) {

				continue;
			}

			_sendDSRequestNotification(dsRequest, dsRequestRecipient);

			count++;
		}

		return count;
	}

	private int _sendDSRequestNotifications(
		long companyId, long groupId, DSRequest dsRequest) {

		if (!_isEnabled(companyId, groupId) || (dsRequest == null) ||
			dsRequest.isTerminal()) {

			return 0;
		}

		return _sendDSRequestNotifications(
			dsRequest,
			ListUtil.filter(
				dsRequest.getDSRequestRecipients(),
				dsRequestRecipient -> ArrayUtil.contains(
					DSRequestRecipientConstants.STATUSES_PENDING,
					dsRequestRecipient.getStatus())));
	}

	private DSDocument _toDSDocument(long fileEntryId) throws Exception {
		FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);

		DSDocument dsDocument = new DSDocument();

		dsDocument.setData(
			Base64.encode(FileUtil.getBytes(fileEntry.getContentStream())));
		dsDocument.setDSDocumentId(String.valueOf(fileEntryId));
		dsDocument.setFileExtension(fileEntry.getExtension());
		dsDocument.setName(fileEntry.getFileName());

		return dsDocument;
	}

	private Date _toDate(LocalDateTime localDateTime) {
		if (localDateTime == null) {
			return null;
		}

		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	private List<DSRequestRecipient> _updateRecipientStatuses(
			long companyId, long groupId,
			Map<String, DSRecipient> dsRecipientsMap,
			long dsRequestId, ObjectDefinition objectDefinition)
		throws Exception {

		List<DSRequestRecipient> sentDSRequestRecipients = new ArrayList<>();

		for (Map<String, Serializable> recipientValues :
				_getValuesList(
					companyId,
					StringBundler.concat(
						"(r_dsRequestToDSRequestRecipients_l_dsRequestId eq '",
						dsRequestId, "')"),
					objectDefinition, null)) {

			DSRecipient dsRecipient = dsRecipientsMap.get(
				MapUtil.getString(recipientValues, "providerRecipientId"));

			if (dsRecipient == null) {
				continue;
			}

			long dsRequestRecipientId = MapUtil.getLong(
				recipientValues, objectDefinition.getPKObjectFieldName());

			ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
				dsRequestRecipientId);

			if (objectEntry == null) {
				continue;
			}

			String requestRecipientStatus = _getRequestRecipientStatus(
				dsRecipient);

			Map<String, Serializable> values =
				HashMapBuilder.<String, Serializable>putAll(
					objectEntry.getValues()
				).put(
					"requestRecipientStatus", requestRecipientStatus
				).put(
					"requestRecipientStatusDate",
					() -> _toDate(dsRecipient.getStatusLocalDateTime())
				).put(
					"sentDate",
					() -> _toDate(dsRecipient.getSentLocalDateTime())
				).build();

			_objectEntryLocalService.updateObjectEntry(
				objectEntry.getUserId(), dsRequestRecipientId,
				ObjectEntryFolderConstants.
					PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
				values,
				_getServiceContext(
					companyId, groupId, objectEntry.getUserId()));

			if (Objects.equals(
					GetterUtil.getString(
						recipientValues.get("requestRecipientStatus")),
					DSRequestRecipientConstants.STATUS_CREATED) &&
				Objects.equals(
					requestRecipientStatus,
					DSRequestRecipientConstants.STATUS_SENT)) {

				sentDSRequestRecipients.add(new DSRequestRecipient(values));
			}
		}

		return sentDSRequestRecipients;
	}

	private void _updateRequestStatus(
			long companyId, long groupId, DSEnvelope dsEnvelope,
			long dsRequestId)
		throws Exception {

		ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
			dsRequestId);

		if (objectEntry == null) {
			return;
		}

		Map<String, Serializable> values =
			HashMapBuilder.<String, Serializable>putAll(
				objectEntry.getValues()
			).put(
				"requestExpirationDate",
				() -> _toDate(dsEnvelope.getExpireLocalDateTime())
			).put(
				"requestStatus", _getRequestStatus(dsEnvelope)
			).put(
				"requestStatusDate",
				() -> _toDate(dsEnvelope.getStatusChangedLocalDateTime())
			).build();

		_objectEntryLocalService.updateObjectEntry(
			objectEntry.getUserId(), dsRequestId,
			ObjectEntryFolderConstants.PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
			values,
			_getServiceContext(companyId, groupId, objectEntry.getUserId()));
	}

	private static final String _RECIPIENTS_RELATIONSHIP_PATH =
		"dsRequestToDSRequestRecipients/";

	private static final Log _log = LogFactoryUtil.getLog(
		DSRequestManagerImpl.class);

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private DLAppLocalService _dlAppLocalService;

	@Reference
	private DSEnvelopeManager _dsEnvelopeManager;

	@Reference(
		target = "(filter.factory.key=" + ObjectDefinitionConstants.STORAGE_TYPE_DEFAULT + ")"
	)
	private FilterFactory<Predicate> _filterFactory;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private Language _language;

	@Reference
	private MailService _mailService;

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private ObjectEntryLocalService _objectEntryLocalService;

	@Reference
	private Portal _portal;

	@Reference
	private SignDSURLProvider _signDSURLProvider;

	@Reference
	private UserLocalService _userLocalService;

}