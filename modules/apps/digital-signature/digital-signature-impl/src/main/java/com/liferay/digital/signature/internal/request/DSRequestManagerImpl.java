/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.internal.request;

import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureConfigurationUtil;
import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.constants.DSRequestRecipientConstants;
import com.liferay.digital.signature.manager.DSEnvelopeManager;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.request.DSRequestManager;
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
import com.liferay.petra.sql.dsl.expression.Predicate;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.search.Sort;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvokerUtil;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.io.Serializable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

		if (!_isEnabled(companyId, groupId) || (dsEnvelope == null) ||
			(fileEntryIds == null)) {

			return;
		}

		ObjectDefinition dsRequestDocumentObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_DOCUMENT", companyId);
		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);
		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);

		if ((dsRequestDocumentObjectDefinition == null) ||
			(dsRequestRecipientObjectDefinition == null) ||
			(dsRequestObjectDefinition == null)) {

			return;
		}

		try {
			TransactionInvokerUtil.invoke(
				_transactionConfig,
				() -> {
					String documentFieldName = _getRelationshipFieldName(
						dsRequestObjectDefinition,
						"dsRequestToDSRequestDocuments");
					String recipientFieldName = _getRelationshipFieldName(
						dsRequestObjectDefinition,
						"dsRequestToDSRequestRecipients");

					if ((documentFieldName == null) ||
						(recipientFieldName == null)) {

						return null;
					}

					ServiceContext serviceContext = _createServiceContext(
						companyId, groupId, userId);

					String languageId = LocaleUtil.toLanguageId(
						LocaleUtil.getSiteDefault());

					ObjectEntry dsRequestObjectEntry =
						_objectEntryLocalService.addObjectEntry(
							0, userId,
							dsRequestObjectDefinition.getObjectDefinitionId(),
							0, languageId,
							HashMapBuilder.<String, Serializable>put(
								"emailSubject", dsEnvelope.getEmailSubject()
							).put(
								"providerKey", "docusign"
							).put(
								"providerRequestId",
								dsEnvelope.getDSEnvelopeId()
							).put(
								"requestExpirationDate",
								() -> _toDate(
									dsEnvelope.getExpireLocalDateTime())
							).put(
								"requestStatus", _toRequestStatus(dsEnvelope)
							).build(),
							serviceContext);

					for (long fileEntryId : fileEntryIds) {
						_objectEntryLocalService.addObjectEntry(
							0, userId,
							dsRequestDocumentObjectDefinition.
								getObjectDefinitionId(),
							0, languageId,
							HashMapBuilder.<String, Serializable>put(
								documentFieldName,
								dsRequestObjectEntry.getObjectEntryId()
							).put(
								"fileEntryId", fileEntryId
							).build(),
							serviceContext);
					}

					for (DSRecipient dsRecipient :
							dsEnvelope.getDSRecipients()) {

						_objectEntryLocalService.addObjectEntry(
							0, userId,
							dsRequestRecipientObjectDefinition.
								getObjectDefinitionId(),
							0, languageId,
							HashMapBuilder.<String, Serializable>put(
								recipientFieldName,
								dsRequestObjectEntry.getObjectEntryId()
							).put(
								"emailAddress", dsRecipient.getEmailAddress()
							).put(
								"name", dsRecipient.getName()
							).put(
								"providerRecipientId",
								dsRecipient.getDSRecipientId()
							).put(
								"r_userToDSRequestRecipients_userId",
								_getRecipientUserId(
									companyId, dsRecipient.getEmailAddress())
							).put(
								"requestRecipientStatus",
								_toRecipientStatus(dsRecipient.getStatus())
							).put(
								"sentDate",
								() -> _toDate(
									dsRecipient.getSentLocalDateTime())
							).build(),
							serviceContext);
					}

					return null;
				});
		}
		catch (Throwable throwable) {
			throw new PortalException(
				"Unable to record the signature request for envelope " +
					dsEnvelope.getDSEnvelopeId(),
				throwable);
		}
	}

	@Override
	public void updateDSRequest(
		long companyId, long groupId, String providerRequestId) {

		if (!_isEnabled(companyId, groupId) ||
			Validator.isNull(providerRequestId)) {

			return;
		}

		ObjectDefinition dsRequestRecipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);
		ObjectDefinition dsRequestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);

		if ((dsRequestRecipientObjectDefinition == null) ||
			(dsRequestObjectDefinition == null)) {

			return;
		}

		try {
			DSEnvelope dsEnvelope = _dsEnvelopeManager.getDSEnvelope(
				companyId, groupId, providerRequestId);

			if (dsEnvelope == null) {
				return;
			}

			String recipientFieldName = _getRelationshipFieldName(
				dsRequestObjectDefinition, "dsRequestToDSRequestRecipients");

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
						companyId,
						StringBundler.concat(
							"(providerRequestId eq '", providerRequestId, "')"),
						dsRequestObjectDefinition, null)) {

				long requestId = GetterUtil.getLong(
					requestValues.get(
						dsRequestObjectDefinition.getPKObjectFieldName()));

				_updateRequestStatus(
					companyId, groupId, dsEnvelope, requestId, requestStatus);

				_updateRecipientStatuses(
					companyId, groupId, dsRecipients,
					dsRequestRecipientObjectDefinition, recipientFieldName,
					requestId);
			}
		}
		catch (Exception exception) {
			_log.error(
				"Unable to sync the signature request for envelope " +
					providerRequestId,
				exception);
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
			ObjectDefinition dsRequestObjectDefinition, String relationshipName)
		throws Exception {

		ObjectRelationship objectRelationship =
			_objectRelationshipLocalService.fetchObjectRelationship(
				dsRequestObjectDefinition.getObjectDefinitionId(),
				relationshipName);

		if (objectRelationship == null) {
			return null;
		}

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectRelationship.getObjectFieldId2());

		return objectField.getName();
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
		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				companyId, groupId);

		if (digitalSignatureConfiguration == null) {
			return false;
		}

		return digitalSignatureConfiguration.enabled();
	}

	private Date _toDate(LocalDateTime localDateTime) {
		if (localDateTime == null) {
			return null;
		}

		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

	private String _toRecipientStatus(String status) {
		status = StringUtil.toLowerCase(GetterUtil.getString(status));

		if (ArrayUtil.contains(DSRequestRecipientConstants.STATUSES, status)) {
			return status;
		}

		return DSRequestRecipientConstants.STATUS_SENT;
	}

	private String _toRequestStatus(DSEnvelope dsEnvelope) {
		String status = StringUtil.toLowerCase(
			GetterUtil.getString(dsEnvelope.getStatus()));

		if (Objects.equals(status, DSRequestConstants.STATUS_VOIDED)) {
			LocalDateTime expireLocalDateTime =
				dsEnvelope.getExpireLocalDateTime();
			LocalDateTime statusChangedLocalDateTime =
				dsEnvelope.getStatusChangedLocalDateTime();

			if ((expireLocalDateTime != null) &&
				(statusChangedLocalDateTime != null) &&
				!statusChangedLocalDateTime.isBefore(expireLocalDateTime)) {

				return DSRequestConstants.STATUS_EXPIRED;
			}
		}

		if (ArrayUtil.contains(DSRequestConstants.STATUSES, status)) {
			return status;
		}

		return DSRequestConstants.STATUS_SENT;
	}

	private void _updateRecipientStatuses(
			long companyId, long groupId, Map<String, DSRecipient> dsRecipients,
			ObjectDefinition dsRequestRecipientObjectDefinition,
			String recipientFieldName, long requestId)
		throws Exception {

		for (Map<String, Serializable> recipientValues :
				_getValuesList(
					companyId,
					StringBundler.concat(
						"(", recipientFieldName, " eq '", requestId, "')"),
					dsRequestRecipientObjectDefinition, null)) {

			DSRecipient dsRecipient = dsRecipients.get(
				GetterUtil.getString(
					recipientValues.get("providerRecipientId")));

			if (dsRecipient == null) {
				continue;
			}

			long recipientId = GetterUtil.getLong(
				recipientValues.get(
					dsRequestRecipientObjectDefinition.getPKObjectFieldName()));

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
				).put(
					"requestRecipientStatusDate",
					() -> _toDate(dsRecipient.getStatusLocalDateTime())
				).put(
					"sentDate",
					() -> _toDate(dsRecipient.getSentLocalDateTime())
				).build();

			_objectEntryLocalService.updateObjectEntry(
				objectEntry.getUserId(), recipientId, 0, values,
				_createServiceContext(
					companyId, groupId, objectEntry.getUserId()));
		}
	}

	private void _updateRequestStatus(
			long companyId, long groupId, DSEnvelope dsEnvelope, long requestId,
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
				"requestExpirationDate",
				() -> _toDate(dsEnvelope.getExpireLocalDateTime())
			).put(
				"requestStatus", requestStatus
			).put(
				"requestStatusDate",
				() -> _toDate(dsEnvelope.getStatusChangedLocalDateTime())
			).build();

		_objectEntryLocalService.updateObjectEntry(
			objectEntry.getUserId(), requestId, 0, values,
			_createServiceContext(companyId, groupId, objectEntry.getUserId()));
	}

	private static final Log _log = LogFactoryUtil.getLog(
		DSRequestManagerImpl.class);

	private static final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

	@Reference
	private DSEnvelopeManager _dsEnvelopeManager;

	@Reference(
		target = "(filter.factory.key=" + ObjectDefinitionConstants.STORAGE_TYPE_DEFAULT + ")"
	)
	private FilterFactory<Predicate> _filterFactory;

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private ObjectEntryLocalService _objectEntryLocalService;

	@Reference
	private ObjectFieldLocalService _objectFieldLocalService;

	@Reference
	private ObjectRelationshipLocalService _objectRelationshipLocalService;

	@Reference
	private UserLocalService _userLocalService;

}