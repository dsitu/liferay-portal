/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.internal.manager;

import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureConfigurationUtil;
import com.liferay.digital.signature.constants.DSObjectDefinitionConstants;
import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.manager.DSEnvelopeManager;
import com.liferay.digital.signature.manager.DSRequestManager;
import com.liferay.digital.signature.model.DSDocument;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.model.DSRequestRecipient;
import com.liferay.digital.signature.url.SignDSURLProvider;
import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.kernel.service.DLFileEntryLocalService;
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
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.feature.flag.FeatureFlagManagerUtil;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.UserConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.FileVersion;
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
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PrefsPropsUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import jakarta.mail.internet.InternetAddress;

import java.io.Serializable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import java.util.ArrayList;
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
		throws PortalException {

		if (ArrayUtil.isEmpty(fileEntryIds)) {
			throw new PortalException(
				"A signature request must have at least one file entry");
		}

		int expireAfter = dsEnvelope.getExpireAfter();

		if ((expireAfter < 0) || (dsEnvelope.getExpireWarn() < 0)) {
			throw new PortalException(
				"Days until expiration and days to warn signers must not be " +
					"negative");
		}

		if ((expireAfter > 0) && (dsEnvelope.getExpireWarn() >= expireAfter)) {
			throw new PortalException(
				"Days to warn signers must be fewer than days until " +
					"expiration");
		}

		if (!_isEnabled(companyId, groupId)) {
			throw new PortalException(
				"Digital signatures are not enabled for group " + groupId);
		}

		Map<Long, DSRequest> dsRequests = getDSRequests(
			companyId, fileEntryIds);

		for (DSRequest dsRequest : dsRequests.values()) {
			if (dsRequest.isRequestable()) {
				updateDSRequest(
					companyId, dsRequest.getSiteGroupId(),
					dsRequest.getProviderRequestId());
			}
		}

		dsRequests = getDSRequests(companyId, ListUtil.fromArray(fileEntryIds));

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

		for (DSRecipient dsRecipient : dsEnvelope.getDSRecipients()) {
			User recipientUser = _userLocalService.getUserByEmailAddress(
				companyId, dsRecipient.getEmailAddress());

			dsRecipient.setDSClientUserId(
				String.valueOf(recipientUser.getUserId()));
			dsRecipient.setName(recipientUser.getFullName());
		}

		User user = _userLocalService.getUser(userId);

		dsEnvelope.setDSDocuments(
			TransformUtil.transformToList(
				fileEntryIds, fileEntryId -> _toDSDocument(fileEntryId)));
		dsEnvelope.setSenderEmailAddress(user.getEmailAddress());
		dsEnvelope.setStatus(DSRequestConstants.STATUS_SENT);

		DSEnvelope sentDSEnvelope = _dsEnvelopeManager.addDSEnvelope(
			companyId, groupId, dsEnvelope);

		try {
			return _addDSRequest(
				companyId, groupId, userId, sentDSEnvelope, fileEntryIds);
		}
		catch (Exception exception1) {
			try {
				_dsEnvelopeManager.voidDSEnvelope(
					companyId, groupId, sentDSEnvelope.getDSEnvelopeId(),
					"Unable to record the signature request");
			}
			catch (Exception exception2) {
				exception1.addSuppressed(exception2);
			}

			if (exception1 instanceof PortalException) {
				throw (PortalException)exception1;
			}

			throw new PortalException(
				"Unable to record the signature request for envelope " +
					sentDSEnvelope.getDSEnvelopeId(),
				exception1);
		}
	}

	@Override
	public DSRequest fetchDSRequest(long companyId, long dsRequestId) {
		ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
			dsRequestId);

		if ((objectEntry == null) ||
			(objectEntry.getCompanyId() != companyId) ||
			!_isEnabled(
				companyId,
				MapUtil.getLong(objectEntry.getValues(), "siteGroupId"))) {

			return null;
		}

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.EXTERNAL_REFERENCE_CODE_REQUEST,
					companyId);

		if ((objectDefinition == null) ||
			(objectEntry.getObjectDefinitionId() !=
				objectDefinition.getObjectDefinitionId())) {

			return null;
		}

		try {
			Map<Long, DSRequest> dsRequests = _getDSRequests(
				companyId, Collections.singleton(dsRequestId));

			return dsRequests.get(dsRequestId);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to load the signature request " + dsRequestId,
				exception);

			return null;
		}
	}

	@Override
	public DSRequest fetchFileEntryDSRequest(long companyId, long fileEntryId) {
		Map<Long, DSRequest> dsRequests = getDSRequests(
			companyId, Collections.singleton(fileEntryId));

		return dsRequests.get(fileEntryId);
	}

	@Override
	public Map<Long, DSRequest> getDSRequests(
		long companyId, long[] fileEntryIds) {

		Map<Long, DSRequest> dsRequests = new HashMap<>();

		if (ArrayUtil.isEmpty(fileEntryIds) ||
			!FeatureFlagManagerUtil.isEnabled(companyId, "LPD-69290")) {

			return dsRequests;
		}

		try {
			Map<Long, Long> dsRequestIds = _getDSRequestIds(
				companyId, fileEntryIds);

			if (dsRequestIds.isEmpty()) {
				return dsRequests;
			}

			Map<Long, DSRequest> dsRequestsMap = _getDSRequests(
				companyId, new HashSet<>(dsRequestIds.values()));

			for (Map.Entry<Long, Long> entry : dsRequestIds.entrySet()) {
				DSRequest dsRequest = dsRequestsMap.get(entry.getValue());

				if ((dsRequest != null) &&
					_isEnabled(companyId, dsRequest.getSiteGroupId())) {

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

		if (!FeatureFlagManagerUtil.isEnabled(companyId, "LPD-69290")) {
			return dsRequests;
		}

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_DOCUMENT,
					companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_RECIPIENT,
					companyId);

		if ((dsRequestDocumentObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null)) {

			return dsRequests;
		}

		try {
			Set<Long> dsRequestIds = new LinkedHashSet<>();

			for (Map<String, Serializable> values :
					_getValuesList(
						companyId, "(fileEntryId eq " + fileEntryId + ")",
						dsRequestDocumentObjectDefinition,
						new Sort[] {
							new Sort(Field.CREATE_DATE, Sort.LONG_TYPE, true)
						})) {

				dsRequestIds.add(
					MapUtil.getLong(
						values,
						"r_dsRequestToDSRequestDocuments_l_dsRequestId"));
			}

			if (dsRequestIds.isEmpty()) {
				return dsRequests;
			}

			Map<Long, DSRequest> dsRequestsMap = _getDSRequestsMap(
				companyId, dsRequestIds, dsRequestRecipientObjectDefinition);

			for (long dsRequestId : dsRequestIds) {
				DSRequest dsRequest = dsRequestsMap.get(dsRequestId);

				if ((dsRequest != null) &&
					_isEnabled(companyId, dsRequest.getSiteGroupId())) {

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
	public List<DSRequest> getSiteDSRequests(
		long companyId, long siteGroupId, String search, int start, int end) {

		if (!_isEnabled(companyId, siteGroupId)) {
			return new ArrayList<>();
		}

		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.EXTERNAL_REFERENCE_CODE_REQUEST,
					companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_RECIPIENT,
					companyId);

		if ((dsRequestObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null)) {

			return new ArrayList<>();
		}

		try {
			List<Long> dsRequestIds = _objectEntryLocalService.getPrimaryKeys(
				new Long[] {0L}, companyId,
				dsRequestObjectDefinition.getUserId(),
				dsRequestObjectDefinition.getObjectDefinitionId(),
				_filterFactory.create(
					"siteGroupId eq " + siteGroupId, dsRequestObjectDefinition),
				false, search, start, end,
				new Sort[] {new Sort(Field.CREATE_DATE, Sort.LONG_TYPE, true)});

			if (dsRequestIds.isEmpty()) {
				return new ArrayList<>();
			}

			Map<Long, DSRequest> dsRequestsMap = _getDSRequestsMap(
				companyId, new HashSet<>(dsRequestIds),
				dsRequestRecipientObjectDefinition);

			return TransformUtil.transform(dsRequestIds, dsRequestsMap::get);
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

		if (!_isEnabled(companyId, siteGroupId)) {
			return 0;
		}

		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.EXTERNAL_REFERENCE_CODE_REQUEST,
					companyId);

		if (dsRequestObjectDefinition == null) {
			return 0;
		}

		try {
			return _objectEntryLocalService.getValuesListCount(
				new Long[] {0L}, companyId,
				dsRequestObjectDefinition.getUserId(),
				dsRequestObjectDefinition.getObjectDefinitionId(),
				_filterFactory.create(
					"siteGroupId eq " + siteGroupId, dsRequestObjectDefinition),
				false, search);
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

		if (Objects.equals(actionId, ActionKeys.VIEW)) {
			User user = permissionChecker.getUser();

			if (dsRequest.getRequesterUserId() == user.getUserId()) {
				return true;
			}

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

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.EXTERNAL_REFERENCE_CODE_REQUEST,
					dsRequest.getCompanyId());

		if (objectDefinition == null) {
			return false;
		}

		ModelResourcePermission<?> modelResourcePermission =
			ModelResourcePermissionRegistryUtil.getModelResourcePermission(
				objectDefinition.getClassName());

		return modelResourcePermission.contains(
			permissionChecker, dsRequest.getDSRequestId(), actionId);
	}

	@Override
	public void sendDSRequestNotifications(
		long companyId, long groupId, DSRequest dsRequest) {

		if ((dsRequest == null) || dsRequest.isTerminal() ||
			!_isEnabled(companyId, groupId)) {

			return;
		}

		_sendDSRequestNotifications(
			dsRequest, dsRequest.getDSRequestRecipients());
	}

	@Override
	public int sendSignatureReminders(long companyId) {
		if (!FeatureFlagManagerUtil.isEnabled(companyId, "LPD-69290")) {
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
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_RECIPIENT,
					companyId);

		if (dsRequestRecipientObjectDefinition == null) {
			return 0;
		}

		int count = 0;

		try {
			Set<Long> dsRequestIds = new HashSet<>();

			for (Map<String, Serializable> values :
					_getValuesList(
						companyId,
						StringBundler.concat(
							"(requestRecipientStatus eq '",
							DSRequestConstants.STATUS_SENT, "')"),
						dsRequestRecipientObjectDefinition, null)) {

				dsRequestIds.add(
					MapUtil.getLong(
						values,
						"r_dsRequestToDSRequestRecipients_l_dsRequestId"));
			}

			if (dsRequestIds.isEmpty()) {
				return 0;
			}

			Map<Long, DSRequest> dsRequestsMap = _getDSRequestsMap(
				companyId, dsRequestIds, dsRequestRecipientObjectDefinition);

			for (DSRequest dsRequest : dsRequestsMap.values()) {
				if (dsRequest.isTerminal() ||
					!_isEnabled(companyId, dsRequest.getSiteGroupId())) {

					continue;
				}

				List<String> providerRecipientIds = TransformUtil.transform(
					dsRequest.getDSRequestRecipients(),
					dsRequestRecipient -> {
						if (!Objects.equals(
								DSRequestConstants.STATUS_SENT,
								dsRequestRecipient.getStatus())) {

							return null;
						}

						return dsRequestRecipient.getProviderRecipientId();
					});

				updateDSRequest(
					companyId, dsRequest.getSiteGroupId(),
					dsRequest.getProviderRequestId());

				DSRequest updatedDSRequest = fetchDSRequest(
					companyId, dsRequest.getDSRequestId());

				if ((updatedDSRequest == null) ||
					updatedDSRequest.isTerminal()) {

					continue;
				}

				count += _sendDSRequestNotifications(
					updatedDSRequest,
					ListUtil.filter(
						updatedDSRequest.getDSRequestRecipients(),
						dsRequestRecipient -> providerRecipientIds.contains(
							dsRequestRecipient.getProviderRecipientId())));
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

		if (Validator.isNull(providerRequestId) ||
			!_isEnabled(companyId, groupId)) {

			return;
		}

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_DOCUMENT,
					companyId);
		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.EXTERNAL_REFERENCE_CODE_REQUEST,
					companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_RECIPIENT,
					companyId);

		if ((dsRequestDocumentObjectDefinition == null) ||
			(dsRequestObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null)) {

			return;
		}

		try {
			Map<String, DSRecipient> dsRecipients = new HashMap<>();

			DSEnvelope dsEnvelope = _dsEnvelopeManager.getDSEnvelope(
				companyId, groupId, providerRequestId);

			for (DSRecipient dsRecipient : dsEnvelope.getDSRecipients()) {
				dsRecipients.put(dsRecipient.getDSRecipientId(), dsRecipient);
			}

			String requestStatus = _getRequestStatus(dsEnvelope);

			for (Map<String, Serializable> values :
					_getValuesList(
						companyId,
						"(providerRequestId eq '" + providerRequestId + "')",
						dsRequestObjectDefinition, null)) {

				long dsRequestId = MapUtil.getLong(
					values, dsRequestObjectDefinition.getPKObjectFieldName());

				List<DSRequestRecipient> sentDSRequestRecipients =
					_updateRecipientStatuses(
						companyId, groupId, dsRecipients, dsRequestId,
						dsRequestRecipientObjectDefinition, requestStatus);

				if (Objects.equals(
						requestStatus, DSRequestConstants.STATUS_COMPLETED) &&
					!Objects.equals(
						MapUtil.getString(values, "requestStatus"),
						DSRequestConstants.STATUS_COMPLETED)) {

					_updateFileEntries(
						companyId, groupId, dsEnvelope,
						dsRequestDocumentObjectDefinition, dsRequestId);
				}

				_updateRequestStatus(
					companyId, groupId, dsEnvelope, dsRequestId);

				if (!sentDSRequestRecipients.isEmpty()) {
					_sendDSRequestNotifications(
						fetchDSRequest(companyId, dsRequestId),
						sentDSRequestRecipients);
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

		if (dsRequest == null) {
			return;
		}

		String providerRequestId = dsRequest.getProviderRequestId();

		if (Validator.isNull(providerRequestId) ||
			!_isEnabled(companyId, groupId)) {

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

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_DOCUMENT,
					companyId);
		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.EXTERNAL_REFERENCE_CODE_REQUEST,
					companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_RECIPIENT,
					companyId);

		if ((dsRequestDocumentObjectDefinition == null) ||
			(dsRequestObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null)) {

			throw new PortalException(
				"Unable to find the signature request object definitions");
		}

		ServiceContext serviceContext = _getServiceContext(
			companyId, groupId, userId);

		ObjectEntry dsRequestObjectEntry =
			_objectEntryLocalService.addObjectEntry(
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
					"requestStatus", () -> _getRequestStatus(dsEnvelope)
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

		List<DSRequestRecipient> dsRequestRecipients = TransformUtil.transform(
			dsEnvelope.getDSRecipients(),
			dsRecipient -> {
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
							() -> _getRequestRecipientStatus(
								dsRecipient, _getRequestStatus(dsEnvelope))
						).put(
							"sentDate",
							() -> _toDate(dsRecipient.getSentLocalDateTime())
						).put(
							"signingOrder",
							Math.max(1, dsRecipient.getRoutingOrder())
						).build(),
						serviceContext);

				return new DSRequestRecipient(
					dsRequestRecipientObjectEntry.getValues());
			});

		return new DSRequest(
			dsRequestObjectEntry.getCompanyId(),
			dsRequestObjectEntry.getCreateDate(),
			dsRequestObjectEntry.getObjectEntryId(), dsRequestRecipients,
			ListUtil.fromArray(fileEntryIds),
			_getRequesterEmailAddress(dsRequestObjectEntry),
			_getRequesterName(dsRequestObjectEntry),
			dsRequestObjectEntry.getUserId(), dsRequestObjectEntry.getValues());
	}

	private Map<Long, Long> _getDSRequestIds(
			long companyId, long[] fileEntryIds)
		throws Exception {

		Map<Long, Long> dsRequestIds = new HashMap<>();

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_DOCUMENT,
					companyId);

		if (objectDefinition == null) {
			return dsRequestIds;
		}

		for (Map<String, Serializable> values :
				_getValuesList(
					companyId,
					StringBundler.concat(
						"(fileEntryId in (",
						StringUtil.merge(fileEntryIds, ", "), "))"),
					objectDefinition,
					new Sort[] {
						new Sort(Field.CREATE_DATE, Sort.LONG_TYPE, true)
					})) {

			dsRequestIds.putIfAbsent(
				MapUtil.getLong(values, "fileEntryId"),
				MapUtil.getLong(
					values, "r_dsRequestToDSRequestDocuments_l_dsRequestId"));
		}

		return dsRequestIds;
	}

	private Map<Long, DSRequest> _getDSRequests(
			long companyId, Set<Long> dsRequestIds)
		throws Exception {

		Map<Long, DSRequest> dsRequests = new HashMap<>();

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_RECIPIENT,
					companyId);

		if (objectDefinition == null) {
			return dsRequests;
		}

		Map<Long, List<DSRequestRecipient>> dsRequestRecipientsMap =
			new HashMap<>();

		for (Map<String, Serializable> values :
				_getValuesList(
					companyId, dsRequestIds,
					"r_dsRequestToDSRequestRecipients_l_dsRequestId",
					objectDefinition)) {

			List<DSRequestRecipient> dsRequestRecipients =
				dsRequestRecipientsMap.computeIfAbsent(
					MapUtil.getLong(
						values,
						"r_dsRequestToDSRequestRecipients_l_dsRequestId"),
					dsRequestId -> new ArrayList<>());

			dsRequestRecipients.add(new DSRequestRecipient(values));
		}

		for (List<DSRequestRecipient> dsRequestRecipients :
				dsRequestRecipientsMap.values()) {

			dsRequestRecipients.sort(
				Comparator.comparingInt(DSRequestRecipient::getSigningOrder));
		}

		Map<Long, List<Long>> fileEntryIds = _getFileEntryIds(
			companyId, dsRequestIds);

		for (long dsRequestId : dsRequestIds) {
			ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
				dsRequestId);

			if (objectEntry == null) {
				continue;
			}

			dsRequests.put(
				dsRequestId,
				new DSRequest(
					objectEntry.getCompanyId(), objectEntry.getCreateDate(),
					dsRequestId,
					dsRequestRecipientsMap.getOrDefault(
						dsRequestId, Collections.emptyList()),
					fileEntryIds.getOrDefault(
						dsRequestId, Collections.emptyList()),
					_getRequesterEmailAddress(objectEntry),
					_getRequesterName(objectEntry), objectEntry.getUserId(),
					objectEntry.getValues()));
		}

		return dsRequests;
	}

	private String _getEmailBody(String emailBody, Locale locale, String url) {
		String message = emailBody;

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

	private Map<Long, List<Long>> _getFileEntryIds(
			long companyId, Set<Long> dsRequestIds)
		throws Exception {

		Map<Long, List<Long>> fileEntryIdsMap = new HashMap<>();

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					DSObjectDefinitionConstants.
						EXTERNAL_REFERENCE_CODE_REQUEST_DOCUMENT,
					companyId);

		if (objectDefinition == null) {
			return fileEntryIdsMap;
		}

		for (Map<String, Serializable> values :
				_getValuesList(
					companyId, dsRequestIds,
					"r_dsRequestToDSRequestDocuments_l_dsRequestId",
					objectDefinition)) {

			List<Long> fileEntryIds = fileEntryIdsMap.computeIfAbsent(
				MapUtil.getLong(
					values, "r_dsRequestToDSRequestDocuments_l_dsRequestId"),
				dsRequestId -> new ArrayList<>());

			fileEntryIds.add(MapUtil.getLong(values, "fileEntryId"));
		}

		return fileEntryIdsMap;
	}

	private Locale _getLocale(long siteGroupId, long userId) throws Exception {
		User user = _userLocalService.fetchUser(userId);

		if (user != null) {
			return user.getLocale();
		}

		return _portal.getSiteDefaultLocale(siteGroupId);
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

	private String _getRequestRecipientStatus(
		DSRecipient dsRecipient, String requestStatus) {

		String status = StringUtil.toLowerCase(dsRecipient.getStatus());

		if ((Objects.equals(requestStatus, DSRequestConstants.STATUS_EXPIRED) ||
			 Objects.equals(requestStatus, DSRequestConstants.STATUS_VOIDED)) &&
			!Objects.equals(status, DSRequestConstants.STATUS_COMPLETED) &&
			!Objects.equals(status, DSRequestConstants.STATUS_DECLINED) &&
			!Objects.equals(status, DSRequestConstants.STATUS_SIGNED)) {

			return requestStatus;
		}

		if (Objects.equals(status, "delivered")) {
			return DSRequestConstants.STATUS_SENT;
		}

		if (ArrayUtil.contains(
				DSRequestConstants.STATUSES_REQUEST_RECIPIENT, status)) {

			return status;
		}

		if (_log.isWarnEnabled()) {
			_log.warn(
				StringBundler.concat(
					"Unsupported request recipient status \"", status, "\""));
		}

		return null;
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

		if (Objects.equals(status, "delivered") ||
			Objects.equals(status, DSRequestConstants.STATUS_SIGNED)) {

			return DSRequestConstants.STATUS_SENT;
		}

		if (ArrayUtil.contains(DSRequestConstants.STATUSES_REQUEST, status)) {
			return status;
		}

		if (_log.isWarnEnabled()) {
			_log.warn(
				StringBundler.concat(
					"Unsupported request status \"", status, "\""));
		}

		return null;
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

	private String _getSignedFileName(FileEntry fileEntry) {
		if (StringUtil.equalsIgnoreCase(fileEntry.getExtension(), "pdf")) {
			return fileEntry.getFileName();
		}

		String fileName = FileUtil.stripExtension(fileEntry.getFileName());

		String signedFileName = fileName + ".pdf";

		for (int i = 1;; i++) {
			DLFileEntry dlFileEntry =
				_dlFileEntryLocalService.fetchFileEntryByFileName(
					fileEntry.getGroupId(), fileEntry.getFolderId(),
					signedFileName);

			if (dlFileEntry == null) {
				return signedFileName;
			}

			if (i == 1) {
				signedFileName = fileName + " (signed).pdf";
			}
			else {
				signedFileName = StringBundler.concat(
					fileName, " (signed ", i, ").pdf");
			}
		}
	}

	private long _getSiteGroupId(long groupId) {
		Group group = _groupLocalService.fetchGroup(groupId);

		if ((group == null) || !group.isSite()) {
			return 0;
		}

		return groupId;
	}

	private List<Map<String, Serializable>> _getValuesList(
			long companyId, Set<Long> dsRequestIds, String fieldName,
			ObjectDefinition objectDefinition)
		throws Exception {

		List<Map<String, Serializable>> valuesList = new ArrayList<>();

		List<Long> dsRequestIdsList = new ArrayList<>(dsRequestIds);

		for (int i = 0; i < dsRequestIdsList.size(); i += _IN_FILTER_MAX_SIZE) {
			valuesList.addAll(
				_getValuesList(
					companyId,
					StringBundler.concat(
						"(", fieldName, " in ('",
						StringUtil.merge(
							ListUtil.subList(
								dsRequestIdsList, i, i + _IN_FILTER_MAX_SIZE),
							"', '"),
						"'))"),
					objectDefinition, null));
		}

		return valuesList;
	}

	private List<Map<String, Serializable>> _getValuesList(
			long companyId, String filterString,
			ObjectDefinition objectDefinition, Sort[] sorts)
		throws Exception {

		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		try {
			PermissionThreadLocal.setPermissionChecker(null);

			return _objectEntryLocalService.getValuesList(
				0, companyId, objectDefinition.getUserId(),
				objectDefinition.getObjectDefinitionId(),
				_filterFactory.create(filterString, objectDefinition), null,
				QueryUtil.ALL_POS, QueryUtil.ALL_POS, sorts);
		}
		finally {
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
		}
	}

	private boolean _isEnabled(long companyId, long groupId) {
		if (!FeatureFlagManagerUtil.isEnabled(companyId, "LPD-69290")) {
			return false;
		}

		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				companyId, groupId);

		if (digitalSignatureConfiguration.embeddedViewEnabled() &&
			digitalSignatureConfiguration.enabled()) {

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

		try {
			Locale locale = _getLocale(
				dsRequest.getSiteGroupId(), dsRequestRecipient.getUserId());

			String subject = dsRequest.getEmailSubject();

			if (Validator.isNull(subject)) {
				subject = _language.get(locale, "you-have-a-document-to-sign");
			}

			String fromAddress = PrefsPropsUtil.getString(
				dsRequest.getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);
			String fromName = PrefsPropsUtil.getString(
				dsRequest.getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_NAME);
			String url = _signDSURLProvider.getURL(
				dsRequest.getCompanyId(), dsRequest.getSiteGroupId(),
				dsRequest.getDSRequestId());

			MailMessage mailMessage = new MailMessage(
				new InternetAddress(fromAddress, fromName),
				new InternetAddress(dsRequestRecipient.getEmailAddress()),
				subject, _getEmailBody(dsRequest.getEmailBody(), locale, url),
				true);

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
			if (!Objects.equals(
					DSRequestConstants.STATUS_SENT,
					dsRequestRecipient.getStatus()) ||
				(dsRequestRecipient.getUserId() <= 0) ||
				!Validator.isEmailAddress(
					dsRequestRecipient.getEmailAddress())) {

				continue;
			}

			_sendDSRequestNotification(dsRequest, dsRequestRecipient);

			count++;
		}

		return count;
	}

	private DSDocument _toDSDocument(long fileEntryId) throws Exception {
		FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);

		return new DSDocument() {
			{
				data = Base64.encode(
					FileUtil.getBytes(fileEntry.getContentStream()));
				dsDocumentId = String.valueOf(fileEntryId);
				fileExtension = fileEntry.getExtension();
				name = fileEntry.getFileName();
			}
		};
	}

	private Date _toDate(LocalDateTime localDateTime) {
		if (localDateTime == null) {
			return null;
		}

		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	private void _updateFileEntries(
			long companyId, long groupId, DSEnvelope dsEnvelope,
			ObjectDefinition objectDefinition, long dsRequestId)
		throws Exception {

		List<Long> fileEntryIds = TransformUtil.transform(
			_getValuesList(
				companyId,
				StringBundler.concat(
					"(r_dsRequestToDSRequestDocuments_l_dsRequestId eq '",
					dsRequestId, "')"),
				objectDefinition, null),
			values -> MapUtil.getLong(values, "fileEntryId"));

		if (fileEntryIds.isEmpty()) {
			return;
		}

		ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
			dsRequestId);

		if (objectEntry == null) {
			return;
		}

		long userId = objectEntry.getUserId();

		for (long fileEntryId : fileEntryIds) {
			byte[] bytes = _dsEnvelopeManager.getSignedDocument(
				companyId, groupId, dsEnvelope.getDSEnvelopeId(),
				String.valueOf(fileEntryId));

			if ((bytes == null) || (bytes.length < 5) ||
				!StringUtil.startsWith(new String(bytes, 0, 5), "%PDF-")) {

				throw new PortalException(
					StringBundler.concat(
						"DocuSign did not return a signed PDF for file entry ",
						fileEntryId, " of envelope ",
						dsEnvelope.getDSEnvelopeId()));
			}

			FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);

			String signedFileName = _getSignedFileName(fileEntry);

			String title = fileEntry.getTitle();

			if (Objects.equals(title, fileEntry.getFileName())) {
				title = signedFileName;
			}

			ServiceContext serviceContext = _getServiceContext(
				companyId, fileEntry.getGroupId(), userId);

			fileEntry = _dlAppLocalService.updateFileEntry(
				userId, fileEntryId, signedFileName,
				ContentTypes.APPLICATION_PDF, title, null,
				fileEntry.getDescription(), null, DLVersionNumberIncrease.MAJOR,
				bytes, fileEntry.getDisplayDate(),
				fileEntry.getExpirationDate(), fileEntry.getReviewDate(),
				serviceContext);

			FileVersion fileVersion = fileEntry.getLatestFileVersion();

			if (fileVersion.getStatus() != WorkflowConstants.STATUS_APPROVED) {
				_dlFileEntryLocalService.updateStatus(
					userId, fileVersion.getFileVersionId(),
					WorkflowConstants.STATUS_APPROVED, serviceContext,
					new HashMap<>());
			}
		}
	}

	private List<DSRequestRecipient> _updateRecipientStatuses(
			long companyId, long groupId, Map<String, DSRecipient> dsRecipients,
			long dsRequestId,
			ObjectDefinition dsRequestRecipientObjectDefinition,
			String requestStatus)
		throws Exception {

		List<DSRequestRecipient> sentDSRequestRecipients = new ArrayList<>();

		for (Map<String, Serializable> values :
				_getValuesList(
					companyId,
					"(r_dsRequestToDSRequestRecipients_l_dsRequestId eq '" +
						dsRequestId + "')",
					dsRequestRecipientObjectDefinition, null)) {

			DSRecipient dsRecipient = dsRecipients.get(
				MapUtil.getString(values, "providerRecipientId"));

			if (dsRecipient == null) {
				continue;
			}

			long dsRequestRecipientId = MapUtil.getLong(
				values,
				dsRequestRecipientObjectDefinition.getPKObjectFieldName());

			ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
				dsRequestRecipientId);

			if (objectEntry == null) {
				continue;
			}

			String requestRecipientStatus = _getRequestRecipientStatus(
				dsRecipient, requestStatus);

			objectEntry = _objectEntryLocalService.partialUpdateObjectEntry(
				objectEntry.getUserId(), dsRequestRecipientId,
				ObjectEntryFolderConstants.
					PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
				HashMapBuilder.<String, Serializable>put(
					"requestRecipientStatus", () -> requestRecipientStatus
				).put(
					"requestRecipientStatusDate",
					() -> _toDate(dsRecipient.getStatusLocalDateTime())
				).put(
					"sentDate",
					() -> _toDate(dsRecipient.getSentLocalDateTime())
				).build(),
				_getServiceContext(
					companyId, groupId, objectEntry.getUserId()));

			if (Objects.equals(
					MapUtil.getString(values, "requestRecipientStatus"),
					DSRequestConstants.STATUS_CREATED) &&
				Objects.equals(
					requestRecipientStatus, DSRequestConstants.STATUS_SENT)) {

				sentDSRequestRecipients.add(
					new DSRequestRecipient(objectEntry.getValues()));
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

		_objectEntryLocalService.partialUpdateObjectEntry(
			objectEntry.getUserId(), dsRequestId,
			ObjectEntryFolderConstants.PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
			HashMapBuilder.<String, Serializable>put(
				"requestExpirationDate",
				() -> _toDate(dsEnvelope.getExpireLocalDateTime())
			).put(
				"requestStatus", () -> _getRequestStatus(dsEnvelope)
			).put(
				"requestStatusDate",
				() -> _toDate(dsEnvelope.getStatusChangedLocalDateTime())
			).build(),
			_getServiceContext(companyId, groupId, objectEntry.getUserId()));
	}

	private static final int _IN_FILTER_MAX_SIZE = 1000;

	private static final Log _log = LogFactoryUtil.getLog(
		DSRequestManagerImpl.class);

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private DLAppLocalService _dlAppLocalService;

	@Reference
	private DLFileEntryLocalService _dlFileEntryLocalService;

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