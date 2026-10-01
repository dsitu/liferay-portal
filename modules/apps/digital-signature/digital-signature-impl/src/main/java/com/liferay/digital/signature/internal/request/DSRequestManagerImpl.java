/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.internal.request;

import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.configuration.DigitalSignatureConfigurationUtil;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.request.DSRequestManager;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.search.Indexer;
import com.liferay.portal.kernel.search.IndexerRegistryUtil;
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
			TransactionInvokerUtil.invoke(
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
								"requestStatus",
								_toRequestStatus(dsEnvelope.getStatus())
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

					for (DSRecipient dsRecipient :
							dsEnvelope.getDSRecipients()) {

						_objectEntryLocalService.addObjectEntry(
							0, userId,
							recipientObjectDefinition.getObjectDefinitionId(),
							0, languageId,
							HashMapBuilder.<String, Serializable>put(
								recipientFieldName,
								requestObjectEntry.getObjectEntryId()
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
								_toDate(dsRecipient.getSentLocalDateTime())
							).build(),
							serviceContext);
					}

					for (long fileEntryId : fileEntryIds) {
						_reindexFileEntry(fileEntryId);
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

	private boolean _isEnabled(long companyId, long groupId) {
		DigitalSignatureConfiguration digitalSignatureConfiguration =
			DigitalSignatureConfigurationUtil.getDigitalSignatureConfiguration(
				companyId, groupId);

		if (digitalSignatureConfiguration == null) {
			return false;
		}

		if (digitalSignatureConfiguration.enabled()) {
			return true;
		}

		return false;
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

	private String _toRequestStatus(String status) {
		status = StringUtil.toLowerCase(GetterUtil.getString(status));

		if (ArrayUtil.contains(_DS_ENVELOPE_STATUSES, status)) {
			return status;
		}

		return "sent";
	}

	private static final String[] _DS_ENVELOPE_STATUSES = {
		"completed", "created", "declined", "sent", "voided"
	};

	private static final String[] _DS_RECIPIENT_STATUSES = {
		"completed", "created", "declined", "sent", "signed"
	};

	private static final Log _log = LogFactoryUtil.getLog(
		DSRequestManagerImpl.class);

	private static final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

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