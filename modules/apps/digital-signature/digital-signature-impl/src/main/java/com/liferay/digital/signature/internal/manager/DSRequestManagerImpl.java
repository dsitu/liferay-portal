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
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.UserConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
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
	public void addDSRequest(
			long companyId, long groupId, long userId, DSEnvelope dsEnvelope,
			long[] fileEntryIds)
		throws PortalException {

		if (!_isEnabled(companyId, groupId)) {
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
					"requestStatus", _getRequestStatus(dsEnvelope)
				).put(
					"siteGroupId", _getSiteGroupId(groupId)
				).build(),
				serviceContext);

		try {
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
				_objectEntryLocalService.addObjectEntry(
					0, userId,
					dsRequestRecipientObjectDefinition.getObjectDefinitionId(),
					ObjectEntryFolderConstants.
						PARENT_OBJECT_ENTRY_FOLDER_ID_DEFAULT,
					null,
					HashMapBuilder.<String, Serializable>put(
						"emailAddress", dsRecipient.getEmailAddress()
					).put(
						"name", dsRecipient.getName()
					).put(
						"providerRecipientId", dsRecipient.getDSRecipientId()
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
						"signingOrder", dsRecipient.getRoutingOrder()
					).build(),
					serviceContext);
			}
		}
		catch (Exception exception) {
			_objectEntryLocalService.deleteObjectEntry(dsRequestObjectEntry);

			throw exception;
		}

		_sendDSRequestNotifications(
			companyId, groupId, dsRequestObjectEntry.getObjectEntryId(),
			dsEnvelope);
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
			Map<Long, DSRequest> dsRequestsByRequestId =
				_getDSRequestsByRequestId(
					companyId, dsRequestRecipientObjectDefinition,
					Collections.singleton(dsRequestId));

			return dsRequestsByRequestId.get(dsRequestId);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to load the signature request " + dsRequestId,
				exception);

			return null;
		}
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
			Map<String, DSRecipient> dsRecipientsMap = new HashMap<>();

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
						dsRequestObjectDefinition)) {

				long dsRequestId = MapUtil.getLong(
					requestValues,
					dsRequestObjectDefinition.getPKObjectFieldName());

				_updateRequestStatus(
					companyId, groupId, dsEnvelope, dsRequestId);

				_updateRecipientStatuses(
					companyId, groupId, dsRecipientsMap, dsRequestId,
					dsRequestRecipientObjectDefinition);

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

	private void _archiveSignedDocument(
			long companyId, long groupId, DSEnvelope dsEnvelope,
			ObjectDefinition dsRequestDocumentObjectDefinition,
			long dsRequestId)
		throws Exception {

		List<Long> fileEntryIds = _getRequestFileEntryIds(
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

	private Map<Long, DSRequest> _getDSRequestsByRequestId(
			long companyId, ObjectDefinition dsRequestRecipientObjectDefinition,
			Set<Long> requestIds)
		throws Exception {

		Map<Long, DSRequest> dsRequestsByRequestId = new HashMap<>();

		Map<Long, List<DSRequestRecipient>> dsRequestRecipientsByRequestId =
			new HashMap<>();

		for (Map<String, Serializable> recipientValues :
				_getValuesList(
					companyId,
					StringBundler.concat(
						"(r_dsRequestToDSRequestRecipients_l_dsRequestId in ('",
						StringUtil.merge(requestIds, "', '"), "'))"),
					dsRequestRecipientObjectDefinition)) {

			List<DSRequestRecipient> dsRequestRecipients =
				dsRequestRecipientsByRequestId.computeIfAbsent(
					GetterUtil.getLong(
						recipientValues.get(
							"r_dsRequestToDSRequestRecipients_l_dsRequestId")),
					dsRequestId -> new ArrayList<>());

			dsRequestRecipients.add(new DSRequestRecipient(recipientValues));
		}

		for (List<DSRequestRecipient> dsRequestRecipients :
				dsRequestRecipientsByRequestId.values()) {

			dsRequestRecipients.sort(
				Comparator.comparingInt(DSRequestRecipient::getSigningOrder));
		}

		Map<Long, List<Long>> fileEntryIdsByRequestId =
			_getFileEntryIdsByRequestId(companyId, requestIds);

		for (long dsRequestId : requestIds) {
			ObjectEntry dsRequestObjectEntry =
				_objectEntryLocalService.fetchObjectEntry(dsRequestId);

			if (dsRequestObjectEntry == null) {
				continue;
			}

			dsRequestsByRequestId.put(
				dsRequestId,
				new DSRequest(
					dsRequestObjectEntry.getCompanyId(),
					dsRequestObjectEntry.getCreateDate(), dsRequestId,
					dsRequestRecipientsByRequestId.getOrDefault(
						dsRequestId, Collections.emptyList()),
					fileEntryIdsByRequestId.getOrDefault(
						dsRequestId, Collections.emptyList()),
					_getRequesterEmailAddress(dsRequestObjectEntry),
					_getRequesterName(dsRequestObjectEntry),
					dsRequestObjectEntry.getUserId(),
					dsRequestObjectEntry.getValues()));
		}

		return dsRequestsByRequestId;
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

	private Map<Long, List<Long>> _getFileEntryIdsByRequestId(
			long companyId, Set<Long> requestIds)
		throws Exception {

		Map<Long, List<Long>> fileEntryIdsByRequestId = new HashMap<>();

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_DOCUMENT", companyId);

		if (dsRequestDocumentObjectDefinition == null) {
			return fileEntryIdsByRequestId;
		}

		for (Map<String, Serializable> documentValues :
				_getValuesList(
					companyId,
					StringBundler.concat(
						"(r_dsRequestToDSRequestDocuments_l_dsRequestId in ('",
						StringUtil.merge(requestIds, "', '"), "'))"),
					dsRequestDocumentObjectDefinition)) {

			List<Long> fileEntryIds = fileEntryIdsByRequestId.computeIfAbsent(
				GetterUtil.getLong(
					documentValues.get(
						"r_dsRequestToDSRequestDocuments_l_dsRequestId")),
				dsRequestId -> new ArrayList<>());

			fileEntryIds.add(MapUtil.getLong(documentValues, "fileEntryId"));
		}

		return fileEntryIdsByRequestId;
	}

	private Locale _getLocale(long companyId, String emailAddress, long groupId)
		throws Exception {

		User user = _userLocalService.fetchUserByEmailAddress(
			companyId, emailAddress);

		if (user != null) {
			return user.getLocale();
		}

		return _portal.getSiteDefaultLocale(groupId);
	}

	private String _getLoginURL(String url) {
		String path = HttpComponentsUtil.getPath(url);

		return HttpComponentsUtil.addParameter(
			StringBundler.concat(
				url.substring(0, url.length() - path.length()),
				_portal.getPathMain(), "/portal/login"),
			"redirect", path);
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

	private List<Long> _getRequestFileEntryIds(
			long companyId, ObjectDefinition dsRequestDocumentObjectDefinition,
			long dsRequestId)
		throws Exception {

		return TransformUtil.transform(
			_getValuesList(
				companyId,
				"(r_dsRequestToDSRequestDocuments_l_dsRequestId eq '" +
					dsRequestId + "')",
				dsRequestDocumentObjectDefinition),
			documentValues -> GetterUtil.getLong(
				documentValues.get("fileEntryId")));
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
			long companyId, String filterString,
			ObjectDefinition objectDefinition)
		throws Exception {

		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		try {
			PermissionThreadLocal.setPermissionChecker(null);

			return _objectEntryLocalService.getValuesList(
				0, companyId, objectDefinition.getUserId(),
				objectDefinition.getObjectDefinitionId(),
				_filterFactory.create(filterString, objectDefinition), null,
				QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
		}
		finally {
			PermissionThreadLocal.setPermissionChecker(permissionChecker);
		}
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
		long companyId, long groupId, long dsRequestId, DSRecipient dsRecipient,
		String emailSubject, String emailMessage) {

		String emailAddress = dsRecipient.getEmailAddress();

		if (!Validator.isEmailAddress(emailAddress)) {
			return;
		}

		try {
			String url = _getLoginURL(
				_signDSURLProvider.getURL(
					companyId, _getSiteGroupId(groupId), dsRequestId));

			String fromAddress = PrefsPropsUtil.getString(
				companyId, PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);
			String fromName = PrefsPropsUtil.getString(
				companyId, PropsKeys.ADMIN_EMAIL_FROM_NAME);

			Locale locale = _getLocale(companyId, emailAddress, groupId);

			String subject = emailSubject;

			if (Validator.isNull(subject)) {
				subject = _language.get(locale, "you-have-a-document-to-sign");
			}

			MailMessage mailMessage = new MailMessage(
				new InternetAddress(fromAddress, fromName),
				new InternetAddress(emailAddress), subject,
				_getEmailBody(emailMessage, locale, url), true);

			_mailService.sendEmail(mailMessage);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to send sign email for signature request " +
					dsRequestId,
				exception);
		}
	}

	private void _sendDSRequestNotifications(
		long companyId, long groupId, long dsRequestId, DSEnvelope dsEnvelope) {

		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				companyId, groupId);

		if (!digitalSignatureConfiguration.enableEmbeddedView() ||
			!Objects.equals(dsEnvelope.getStatus(), "sent")) {

			return;
		}

		for (DSRecipient dsRecipient : dsEnvelope.getDSRecipients()) {
			if (Validator.isNotNull(dsRecipient.getDSClientUserId()) &&
				Objects.equals(
					StringUtil.toLowerCase(dsRecipient.getStatus()), "sent")) {

				_sendDSRequestNotification(
					companyId, groupId, dsRequestId, dsRecipient,
					dsEnvelope.getEmailSubject(), dsEnvelope.getEmailBlurb());
			}
		}
	}

	private Date _toDate(LocalDateTime localDateTime) {
		if (localDateTime == null) {
			return null;
		}

		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	private void _updateRecipientStatuses(
			long companyId, long groupId,
			Map<String, DSRecipient> dsRecipientsMap, long dsRequestId,
			ObjectDefinition objectDefinition)
		throws Exception {

		for (Map<String, Serializable> recipientValues :
				_getValuesList(
					companyId,
					"(r_dsRequestToDSRequestRecipients_l_dsRequestId eq '" +
						dsRequestId + "')",
					objectDefinition)) {

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

			Map<String, Serializable> values =
				HashMapBuilder.<String, Serializable>putAll(
					objectEntry.getValues()
				).put(
					"requestRecipientStatus",
					_getRequestRecipientStatus(dsRecipient)
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
		}
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