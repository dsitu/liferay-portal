/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.internal.request;

import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureConfigurationUtil;
import com.liferay.digital.signature.constants.DigitalSignatureConstants;
import com.liferay.digital.signature.manager.DSEnvelopeManager;
import com.liferay.digital.signature.model.DSDocument;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.model.DSRequestRecipient;
import com.liferay.digital.signature.request.DSRequestManager;
import com.liferay.digital.signature.url.SignDSURLProvider;
import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.mail.kernel.model.MailMessage;
import com.liferay.mail.kernel.service.MailService;
import com.liferay.object.constants.ObjectDefinitionConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.rest.filter.factory.FilterFactory;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
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
import com.liferay.portal.kernel.search.Indexer;
import com.liferay.portal.kernel.search.IndexerRegistryUtil;
import com.liferay.portal.kernel.search.Sort;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvokerUtil;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.Base64;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HtmlUtil;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
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
		dsEnvelope.setStatus(_STATUS_SENT);

		DSEnvelope sentDSEnvelope = _dsEnvelopeManager.addDSEnvelope(
			companyId, groupId, dsEnvelope);

		return _addDSRequest(
			companyId, groupId, userId, sentDSEnvelope, fileEntryIds);
	}

	@Override
	public DSRequest fetchDSRequest(long requestId) {
		ObjectEntry requestObjectEntry =
			_objectEntryLocalService.fetchObjectEntry(requestId);

		if (requestObjectEntry == null) {
			return null;
		}

		long companyId = requestObjectEntry.getCompanyId();

		if (!_isEnabled(companyId, 0)) {
			return null;
		}

		ObjectDefinition recipientObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST_RECIPIENT");
		ObjectDefinition requestObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST");

		if ((recipientObjectDefinition == null) ||
			(requestObjectDefinition == null) ||
			(requestObjectEntry.getObjectDefinitionId() !=
				requestObjectDefinition.getObjectDefinitionId())) {

			return null;
		}

		try {
			Map<Long, DSRequest> dsRequestsByRequestId =
				_getDSRequestsByRequestId(
					companyId, recipientObjectDefinition,
					requestObjectDefinition, Collections.singleton(requestId));

			return dsRequestsByRequestId.get(requestId);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to load the signature request " + requestId, exception);

			return null;
		}
	}

	@Override
	public Map<Long, DSRequest> getDSRequests(
		long companyId, Collection<Long> fileEntryIds) {

		Map<Long, DSRequest> dsRequests = new HashMap<>();

		if (!_isEnabled(companyId, 0) || (fileEntryIds == null) ||
			fileEntryIds.isEmpty()) {

			return dsRequests;
		}

		ObjectDefinition documentObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST_DOCUMENT");
		ObjectDefinition recipientObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST_RECIPIENT");
		ObjectDefinition requestObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST");

		if ((documentObjectDefinition == null) ||
			(recipientObjectDefinition == null) ||
			(requestObjectDefinition == null)) {

			return dsRequests;
		}

		try {
			Map<Long, Long> requestIdsByFileEntryId =
				_getRequestIdsByFileEntryId(
					companyId, documentObjectDefinition,
					requestObjectDefinition, fileEntryIds);

			if (requestIdsByFileEntryId.isEmpty()) {
				return dsRequests;
			}

			Map<Long, DSRequest> dsRequestsByRequestId =
				_getDSRequestsByRequestId(
					companyId, recipientObjectDefinition,
					requestObjectDefinition,
					new HashSet<>(requestIdsByFileEntryId.values()));

			for (Map.Entry<Long, Long> entry :
					requestIdsByFileEntryId.entrySet()) {

				DSRequest dsRequest = dsRequestsByRequestId.get(
					entry.getValue());

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
	public void sendDSRequestNotifications(
		long companyId, long groupId, DSRequest dsRequest) {

		_sendDSRequestNotifications(companyId, groupId, dsRequest);
	}

	@Override
	public void updateDSRequest(
		long companyId, long groupId, String providerRequestId) {

		if (!_isEnabled(companyId, groupId) ||
			Validator.isNull(providerRequestId)) {

			return;
		}

		ObjectDefinition documentObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST_DOCUMENT");
		ObjectDefinition recipientObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST_RECIPIENT");
		ObjectDefinition requestObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST");

		if ((documentObjectDefinition == null) ||
			(recipientObjectDefinition == null) ||
			(requestObjectDefinition == null)) {

			return;
		}

		try {
			DSEnvelope dsEnvelope = _dsEnvelopeManager.getDSEnvelope(
				companyId, groupId, providerRequestId);

			if (dsEnvelope == null) {
				return;
			}

			String recipientFieldName = _getRelationshipFieldName(
				requestObjectDefinition, "dsRequestToDSRequestRecipients");

			if (recipientFieldName == null) {
				return;
			}

			Map<String, DSRecipient> dsRecipients = new HashMap<>();

			for (DSRecipient dsRecipient : dsEnvelope.getDSRecipients()) {
				dsRecipients.put(dsRecipient.getDSRecipientId(), dsRecipient);
			}

			String requestStatus = _toRequestStatus(dsEnvelope);

			for (Map<String, Serializable> requestValues :
					_getValuesList(
						companyId, requestObjectDefinition,
						StringBundler.concat(
							"(providerRequestId eq '", providerRequestId, "')"),
						null)) {

				long requestId = GetterUtil.getLong(
					requestValues.get(
						requestObjectDefinition.getPKObjectFieldName()));

				_updateRequestStatus(
					companyId, groupId, requestId, dsEnvelope, requestStatus);

				_updateRecipientStatuses(
					companyId, groupId, recipientObjectDefinition,
					recipientFieldName, requestId, dsRecipients);

				_reindexRequestDocuments(
					companyId, documentObjectDefinition,
					requestObjectDefinition, requestId);

				if (Objects.equals(requestStatus, "completed") &&
					!Objects.equals(
						GetterUtil.getString(
							requestValues.get("requestStatus")),
						"completed")) {

					_archiveSignedDocument(
						companyId, groupId, documentObjectDefinition,
						dsEnvelope, requestObjectDefinition, requestId);
				}
			}
		}
		catch (Exception exception) {
			_log.error(
				"Unable to sync the signature request for envelope " +
					providerRequestId,
				exception);
		}
	}

	@Override
	public void voidDSRequest(
		long companyId, long groupId, String providerRequestId, String reason) {

		if (!_isEnabled(companyId, groupId) ||
			Validator.isNull(providerRequestId)) {

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

		if (!_isEnabled(companyId, groupId) || (dsEnvelope == null) ||
			(fileEntryIds == null)) {

			return null;
		}

		ObjectDefinition documentObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST_DOCUMENT");
		ObjectDefinition recipientObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST_RECIPIENT");
		ObjectDefinition requestObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST");

		if ((documentObjectDefinition == null) ||
			(recipientObjectDefinition == null) ||
			(requestObjectDefinition == null)) {

			return null;
		}

		try {
			return TransactionInvokerUtil.invoke(
				_transactionConfig,
				() -> {
					String documentFieldName = _getRelationshipFieldName(
						requestObjectDefinition,
						"dsRequestToDSRequestDocuments");
					String recipientFieldName = _getRelationshipFieldName(
						requestObjectDefinition,
						"dsRequestToDSRequestRecipients");

					if ((documentFieldName == null) ||
						(recipientFieldName == null)) {

						return null;
					}

					ServiceContext serviceContext = _createServiceContext(
						companyId, groupId, userId);

					String languageId = LocaleUtil.toLanguageId(
						LocaleUtil.getSiteDefault());

					ObjectEntry requestObjectEntry =
						_objectEntryLocalService.addObjectEntry(
							0, userId,
							requestObjectDefinition.getObjectDefinitionId(), 0,
							languageId,
							HashMapBuilder.<String, Serializable>put(
								"emailBody", dsEnvelope.getEmailBlurb()
							).put(
								"emailSubject", dsEnvelope.getEmailSubject()
							).put(
								"providerKey", "docusign"
							).put(
								"providerRequestId",
								dsEnvelope.getDSEnvelopeId()
							).put(
								"requestExpirationDate",
								_toDate(dsEnvelope.getExpireLocalDateTime())
							).put(
								"requestStatus", _toRequestStatus(dsEnvelope)
							).put(
								"siteId", _getSiteId(groupId)
							).build(),
							serviceContext);

					for (long fileEntryId : fileEntryIds) {
						_objectEntryLocalService.addObjectEntry(
							0, userId,
							documentObjectDefinition.getObjectDefinitionId(), 0,
							languageId,
							HashMapBuilder.<String, Serializable>put(
								documentFieldName,
								requestObjectEntry.getObjectEntryId()
							).put(
								"fileEntryId", fileEntryId
							).build(),
							serviceContext);
					}

					List<DSRequestRecipient> dsRequestRecipients =
						new ArrayList<>();

					for (DSRecipient dsRecipient :
							dsEnvelope.getDSRecipients()) {

						ObjectEntry recipientObjectEntry =
							_objectEntryLocalService.addObjectEntry(
								0, userId,
								recipientObjectDefinition.
									getObjectDefinitionId(),
								0, languageId,
								HashMapBuilder.<String, Serializable>put(
									recipientFieldName,
									requestObjectEntry.getObjectEntryId()
								).put(
									"emailAddress",
									dsRecipient.getEmailAddress()
								).put(
									"name", dsRecipient.getName()
								).put(
									"providerRecipientId",
									dsRecipient.getDSRecipientId()
								).put(
									"r_userToDSRequestRecipients_userId",
									_getRecipientUserId(
										companyId,
										dsRecipient.getEmailAddress())
								).put(
									"requestRecipientStatus",
									_toRecipientStatus(dsRecipient.getStatus())
								).put(
									"sentDate",
									_toDate(dsRecipient.getSentLocalDateTime())
								).put(
									"signingOrder",
									Math.max(1, dsRecipient.getRoutingOrder())
								).build(),
								serviceContext);

						dsRequestRecipients.add(
							new DSRequestRecipient(
								recipientObjectEntry.getValues()));
					}

					for (long fileEntryId : fileEntryIds) {
						_reindexFileEntry(fileEntryId);
					}

					return new DSRequest(
						requestObjectEntry.getCompanyId(),
						requestObjectEntry.getCreateDate(),
						requestObjectEntry.getObjectEntryId(),
						dsRequestRecipients,
						ListUtil.fromArray(ArrayUtil.toArray(fileEntryIds)),
						_getRequesterEmailAddress(requestObjectEntry),
						_getRequesterName(requestObjectEntry),
						requestObjectEntry.getUserId(),
						requestObjectEntry.getValues());
				});
		}
		catch (Throwable throwable) {
			try {
				_dsEnvelopeManager.voidDSEnvelope(
					companyId, groupId, dsEnvelope.getDSEnvelopeId(),
					"Unable to record the signature request");
			}
			catch (Exception exception) {
				throwable.addSuppressed(exception);
			}

			throw new PortalException(
				"Unable to record the signature request for envelope " +
					dsEnvelope.getDSEnvelopeId(),
				throwable);
		}
	}

	private void _archiveSignedDocument(
			long companyId, long groupId,
			ObjectDefinition documentObjectDefinition, DSEnvelope dsEnvelope,
			ObjectDefinition requestObjectDefinition, long requestId)
		throws Exception {

		List<Long> fileEntryIds = _getRequestFileEntryIds(
			companyId, documentObjectDefinition, requestObjectDefinition,
			requestId);

		if (fileEntryIds.isEmpty()) {
			return;
		}

		ObjectEntry requestObjectEntry =
			_objectEntryLocalService.fetchObjectEntry(requestId);

		if (requestObjectEntry == null) {
			return;
		}

		long userId = requestObjectEntry.getUserId();

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
				_createServiceContext(
					companyId, fileEntry.getGroupId(), userId));
		}
	}

	private ServiceContext _createServiceContext(
		long companyId, long groupId, long userId) {

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setCompanyId(companyId);
		serviceContext.setScopeGroupId(groupId);
		serviceContext.setUserId(userId);

		return serviceContext;
	}

	private ObjectDefinition _fetchObjectDefinition(
		long companyId, String externalReferenceCode) {

		return _objectDefinitionLocalService.
			fetchObjectDefinitionByExternalReferenceCode(
				externalReferenceCode, companyId);
	}

	private List<DSDocument> _getDSDocuments(long[] fileEntryIds)
		throws Exception {

		return TransformUtil.transformToList(
			fileEntryIds, fileEntryId -> _toDSDocument(fileEntryId));
	}

	private Map<Long, DSRequest> _getDSRequestsByRequestId(
			long companyId, ObjectDefinition recipientObjectDefinition,
			ObjectDefinition requestObjectDefinition, Set<Long> requestIds)
		throws Exception {

		Map<Long, DSRequest> dsRequestsByRequestId = new HashMap<>();

		String recipientFieldName = _getRelationshipFieldName(
			requestObjectDefinition, "dsRequestToDSRequestRecipients");

		if (recipientFieldName == null) {
			return dsRequestsByRequestId;
		}

		Map<Long, List<DSRequestRecipient>> dsRequestRecipientsByRequestId =
			new HashMap<>();

		for (Map<String, Serializable> recipientValues :
				_getValuesList(
					companyId, recipientObjectDefinition,
					StringBundler.concat(
						"(", recipientFieldName, " in ('",
						StringUtil.merge(requestIds, "', '"), "'))"),
					null)) {

			List<DSRequestRecipient> dsRequestRecipients =
				dsRequestRecipientsByRequestId.computeIfAbsent(
					GetterUtil.getLong(recipientValues.get(recipientFieldName)),
					requestId -> new ArrayList<>());

			dsRequestRecipients.add(new DSRequestRecipient(recipientValues));
		}

		for (List<DSRequestRecipient> dsRequestRecipients :
				dsRequestRecipientsByRequestId.values()) {

			dsRequestRecipients.sort(
				Comparator.comparingInt(DSRequestRecipient::getSigningOrder));
		}

		Map<Long, List<Long>> fileEntryIdsByRequestId =
			_getFileEntryIdsByRequestId(
				companyId, requestObjectDefinition, requestIds);

		for (long requestId : requestIds) {
			ObjectEntry requestObjectEntry =
				_objectEntryLocalService.fetchObjectEntry(requestId);

			if (requestObjectEntry == null) {
				continue;
			}

			dsRequestsByRequestId.put(
				requestId,
				new DSRequest(
					requestObjectEntry.getCompanyId(),
					requestObjectEntry.getCreateDate(), requestId,
					dsRequestRecipientsByRequestId.getOrDefault(
						requestId, Collections.emptyList()),
					fileEntryIdsByRequestId.getOrDefault(
						requestId, Collections.emptyList()),
					_getRequesterEmailAddress(requestObjectEntry),
					_getRequesterName(requestObjectEntry),
					requestObjectEntry.getUserId(),
					requestObjectEntry.getValues()));
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
			long companyId, ObjectDefinition requestObjectDefinition,
			Set<Long> requestIds)
		throws Exception {

		Map<Long, List<Long>> fileEntryIdsByRequestId = new HashMap<>();

		ObjectDefinition documentObjectDefinition = _fetchObjectDefinition(
			companyId, "L_DS_REQUEST_DOCUMENT");
		String documentFieldName = _getRelationshipFieldName(
			requestObjectDefinition, "dsRequestToDSRequestDocuments");

		if ((documentObjectDefinition == null) || (documentFieldName == null)) {
			return fileEntryIdsByRequestId;
		}

		for (Map<String, Serializable> documentValues :
				_getValuesList(
					companyId, documentObjectDefinition,
					StringBundler.concat(
						"(", documentFieldName, " in ('",
						StringUtil.merge(requestIds, "', '"), "'))"),
					null)) {

			List<Long> fileEntryIds = fileEntryIdsByRequestId.computeIfAbsent(
				GetterUtil.getLong(documentValues.get(documentFieldName)),
				requestId -> new ArrayList<>());

			fileEntryIds.add(
				GetterUtil.getLong(documentValues.get("fileEntryId")));
		}

		return fileEntryIdsByRequestId;
	}

	private Locale _getLocale(long userId, long siteId) throws Exception {
		User user = _userLocalService.fetchUser(userId);

		if (user != null) {
			return user.getLocale();
		}

		return _portal.getSiteDefaultLocale(siteId);
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

	private String _getRelationshipFieldName(
			ObjectDefinition requestObjectDefinition, String relationshipName)
		throws Exception {

		ObjectRelationship objectRelationship =
			_objectRelationshipLocalService.fetchObjectRelationship(
				requestObjectDefinition.getObjectDefinitionId(),
				relationshipName);

		if (objectRelationship == null) {
			return null;
		}

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectRelationship.getObjectFieldId2());

		return objectField.getName();
	}

	private List<Long> _getRequestFileEntryIds(
			long companyId, ObjectDefinition documentObjectDefinition,
			ObjectDefinition requestObjectDefinition, long requestId)
		throws Exception {

		String documentFieldName = _getRelationshipFieldName(
			requestObjectDefinition, "dsRequestToDSRequestDocuments");

		if (documentFieldName == null) {
			return Collections.emptyList();
		}

		return TransformUtil.transform(
			_getValuesList(
				companyId, documentObjectDefinition,
				StringBundler.concat(
					"(", documentFieldName, " eq '", requestId, "')"),
				null),
			documentValues -> GetterUtil.getLong(
				documentValues.get("fileEntryId")));
	}

	private Map<Long, Long> _getRequestIdsByFileEntryId(
			long companyId, ObjectDefinition documentObjectDefinition,
			ObjectDefinition requestObjectDefinition,
			Collection<Long> fileEntryIds)
		throws Exception {

		Map<Long, Long> requestIdsByFileEntryId = new HashMap<>();

		String documentFieldName = _getRelationshipFieldName(
			requestObjectDefinition, "dsRequestToDSRequestDocuments");

		if (documentFieldName == null) {
			return requestIdsByFileEntryId;
		}

		for (Map<String, Serializable> documentValues :
				_getValuesList(
					companyId, documentObjectDefinition,
					StringBundler.concat(
						"(fileEntryId in (",
						StringUtil.merge(fileEntryIds, ", "), "))"),
					new Sort[] {
						new Sort(Field.CREATE_DATE, Sort.LONG_TYPE, true)
					})) {

			requestIdsByFileEntryId.putIfAbsent(
				GetterUtil.getLong(documentValues.get("fileEntryId")),
				GetterUtil.getLong(documentValues.get(documentFieldName)));
		}

		return requestIdsByFileEntryId;
	}

	private String _getRequesterEmailAddress(ObjectEntry requestObjectEntry) {
		User user = _userLocalService.fetchUser(requestObjectEntry.getUserId());

		if (user == null) {
			return null;
		}

		if (_isServiceAccount(user)) {
			return PrefsPropsUtil.getString(
				user.getCompanyId(), PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);
		}

		return user.getEmailAddress();
	}

	private String _getRequesterName(ObjectEntry requestObjectEntry) {
		User user = _userLocalService.fetchUser(requestObjectEntry.getUserId());

		if (user == null) {
			return requestObjectEntry.getUserName();
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

	private long _getSiteId(long groupId) {
		Group group = _groupLocalService.fetchGroup(groupId);

		if ((group == null) || !group.isSite()) {
			return 0;
		}

		return groupId;
	}

	private List<Map<String, Serializable>> _getValuesList(
			long companyId, ObjectDefinition objectDefinition,
			String filterString, Sort[] sorts)
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
		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				companyId, groupId);

		if (digitalSignatureConfiguration == null) {
			return false;
		}

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

	private void _putIfNotNull(
		Map<String, Serializable> values, String name, Serializable value) {

		if (value != null) {
			values.put(name, value);
		}
	}

	private void _reindexFileEntry(long fileEntryId) {
		try {
			Indexer<?> indexer = IndexerRegistryUtil.nullSafeGetIndexer(
				"com.liferay.document.library.kernel.model.DLFileEntry");

			indexer.reindex(
				"com.liferay.document.library.kernel.model.DLFileEntry",
				fileEntryId);
		}
		catch (Exception exception) {
			_log.error(
				"Unable to reindex file entry " + fileEntryId, exception);
		}
	}

	private void _reindexRequestDocuments(
			long companyId, ObjectDefinition documentObjectDefinition,
			ObjectDefinition requestObjectDefinition, long requestId)
		throws Exception {

		String documentFieldName = _getRelationshipFieldName(
			requestObjectDefinition, "dsRequestToDSRequestDocuments");

		if (documentFieldName == null) {
			return;
		}

		for (Map<String, Serializable> documentValues :
				_getValuesList(
					companyId, documentObjectDefinition,
					StringBundler.concat(
						"(", documentFieldName, " eq '", requestId, "')"),
					null)) {

			_reindexFileEntry(
				GetterUtil.getLong(documentValues.get("fileEntryId")));
		}
	}

	private void _sendDSRequestNotification(
		DSRequest dsRequest, DSRequestRecipient dsRequestRecipient) {

		String emailAddress = dsRequestRecipient.getEmailAddress();

		if (!Validator.isEmailAddress(emailAddress)) {
			return;
		}

		long companyId = dsRequest.getCompanyId();

		try {
			String url = _getLoginURL(
				_signDSURLProvider.getURL(
					companyId, dsRequest.getSiteId(),
					dsRequest.getDSRequestId()));

			String fromAddress = PrefsPropsUtil.getString(
				companyId, PropsKeys.ADMIN_EMAIL_FROM_ADDRESS);
			String fromName = PrefsPropsUtil.getString(
				companyId, PropsKeys.ADMIN_EMAIL_FROM_NAME);

			Locale locale = _getLocale(
				dsRequestRecipient.getUserId(), dsRequest.getSiteId());

			String subject = dsRequest.getEmailSubject();

			if (Validator.isNull(subject)) {
				subject = _language.get(locale, "you-have-a-document-to-sign");
			}

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
					DigitalSignatureConstants.
						REQUEST_RECIPIENT_STATUSES_PENDING,
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

	private String _toRecipientStatus(String status) {
		status = StringUtil.toLowerCase(GetterUtil.getString(status));

		if (ArrayUtil.contains(_DS_RECIPIENT_STATUSES, status)) {
			return status;
		}

		return "sent";
	}

	private String _toRequestStatus(DSEnvelope dsEnvelope) {
		String status = StringUtil.toLowerCase(
			GetterUtil.getString(dsEnvelope.getStatus()));

		if (Objects.equals(status, "voided")) {
			LocalDateTime expireLocalDateTime =
				dsEnvelope.getExpireLocalDateTime();
			LocalDateTime statusChangedLocalDateTime =
				dsEnvelope.getStatusChangedLocalDateTime();

			if ((expireLocalDateTime != null) &&
				(statusChangedLocalDateTime != null) &&
				!statusChangedLocalDateTime.isBefore(expireLocalDateTime)) {

				return "expired";
			}
		}

		if (ArrayUtil.contains(_DS_ENVELOPE_STATUSES, status)) {
			return status;
		}

		return "sent";
	}

	private void _updateRecipientStatuses(
			long companyId, long groupId,
			ObjectDefinition recipientObjectDefinition,
			String recipientFieldName, long requestId,
			Map<String, DSRecipient> dsRecipients)
		throws Exception {

		for (Map<String, Serializable> recipientValues :
				_getValuesList(
					companyId, recipientObjectDefinition,
					StringBundler.concat(
						"(", recipientFieldName, " eq '", requestId, "')"),
					null)) {

			DSRecipient dsRecipient = dsRecipients.get(
				GetterUtil.getString(
					recipientValues.get("providerRecipientId")));

			if (dsRecipient == null) {
				continue;
			}

			long recipientId = GetterUtil.getLong(
				recipientValues.get(
					recipientObjectDefinition.getPKObjectFieldName()));

			ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
				recipientId);

			if (objectEntry == null) {
				continue;
			}

			Map<String, Serializable> values =
				HashMapBuilder.<String, Serializable>putAll(
					objectEntry.getValues()
				).put(
					"requestRecipientStatus",
					_toRecipientStatus(dsRecipient.getStatus())
				).build();

			_putIfNotNull(
				values, "requestRecipientStatusDate",
				_toDate(dsRecipient.getStatusLocalDateTime()));
			_putIfNotNull(
				values, "sentDate",
				_toDate(dsRecipient.getSentLocalDateTime()));

			_objectEntryLocalService.updateObjectEntry(
				objectEntry.getUserId(), recipientId, 0, values,
				_createServiceContext(
					companyId, groupId, objectEntry.getUserId()));
		}
	}

	private void _updateRequestStatus(
			long companyId, long groupId, long requestId, DSEnvelope dsEnvelope,
			String requestStatus)
		throws Exception {

		ObjectEntry objectEntry = _objectEntryLocalService.fetchObjectEntry(
			requestId);

		if (objectEntry == null) {
			return;
		}

		Map<String, Serializable> values =
			HashMapBuilder.<String, Serializable>putAll(
				objectEntry.getValues()
			).put(
				"requestStatus", requestStatus
			).build();

		_putIfNotNull(
			values, "requestExpirationDate",
			_toDate(dsEnvelope.getExpireLocalDateTime()));
		_putIfNotNull(
			values, "requestStatusDate",
			_toDate(dsEnvelope.getStatusChangedLocalDateTime()));

		_objectEntryLocalService.updateObjectEntry(
			objectEntry.getUserId(), requestId, 0, values,
			_createServiceContext(companyId, groupId, objectEntry.getUserId()));
	}

	private static final String[] _DS_ENVELOPE_STATUSES = {
		"completed", "created", "declined", "expired", "sent", "voided"
	};

	private static final String[] _DS_RECIPIENT_STATUSES = {
		"completed", "created", "declined", "sent", "signed"
	};

	private static final String _STATUS_SENT = "sent";

	private static final Log _log = LogFactoryUtil.getLog(
		DSRequestManagerImpl.class);

	private static final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

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
	private ObjectFieldLocalService _objectFieldLocalService;

	@Reference
	private ObjectRelationshipLocalService _objectRelationshipLocalService;

	@Reference
	private Portal _portal;

	@Reference
	private SignDSURLProvider _signDSURLProvider;

	@Reference
	private UserLocalService _userLocalService;

}