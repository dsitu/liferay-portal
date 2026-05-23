/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.messaging;

import com.liferay.account.constants.AccountRoleConstants;
import com.liferay.account.model.AccountEntry;
import com.liferay.account.service.AccountEntryLocalService;
import com.liferay.ai.hub.internal.constants.AIHubDestinationNames;
import com.liferay.ai.hub.internal.constants.NotificationConstants;
import com.liferay.notification.context.NotificationContextBuilder;
import com.liferay.notification.model.NotificationTemplate;
import com.liferay.notification.service.NotificationTemplateLocalService;
import com.liferay.notification.type.NotificationType;
import com.liferay.notification.type.NotificationTypeServiceTracker;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.audit.AuditMessage;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.messaging.BaseMessageListener;
import com.liferay.portal.kernel.messaging.Destination;
import com.liferay.portal.kernel.messaging.DestinationConfiguration;
import com.liferay.portal.kernel.messaging.DestinationFactory;
import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.model.UserGroupRole;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserGroupRoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.DateUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.PrefsPropsUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.text.DateFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Danny Situ
 */
@Component(
	property = "destination.name=" + AIHubDestinationNames.AI_HUB_ALERT_ROUTING,
	service = MessageListener.class
)
public class AIHubAlertRoutingMessageListener extends BaseMessageListener {

	@Activate
	protected void activate(BundleContext bundleContext) {
		Destination destination = _destinationFactory.createDestination(
			DestinationConfiguration.createParallelDestinationConfiguration(
				AIHubDestinationNames.AI_HUB_ALERT_ROUTING));

		_destinationServiceRegistration = bundleContext.registerService(
			Destination.class, destination,
			MapUtil.singletonDictionary(
				"destination.name", destination.getName()));
	}

	@Deactivate
	protected void deactivate() {
		_destinationServiceRegistration.unregister();
	}

	@Override
	protected void doReceive(Message message) throws Exception {
		AuditMessage auditMessage = new AuditMessage(
			(String)message.getPayload());

		JSONObject additionalInfoJSONObject = auditMessage.getAdditionalInfo();

		// Not sure if these are provided or we need to implement logic to
		// classify.

		String alertTrigger = additionalInfoJSONObject.getString(
			"alertTrigger");
		String severity = additionalInfoJSONObject.getString("severity");

		if (Validator.isNull(alertTrigger) || Validator.isNull(severity)) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Skipping AI guardrail alert with missing incident type " +
						"or severity");
			}

			return;
		}

		long accountEntryId = GetterUtil.getLong(
			additionalInfoJSONObject.getString("accountEntryId"));

		ObjectEntry objectEntry = _fetchNotificationSettingObjectEntry(
			auditMessage.getCompanyId(), accountEntryId);

		if (!_isSeverityEnabled(objectEntry, severity)) {
			return;
		}

		_sendNotifications(auditMessage, accountEntryId, objectEntry);
	}

	private ObjectEntry _fetchNotificationSettingObjectEntry(
		long companyId, long accountEntryId) {

		try {
			ObjectDefinition objectDefinition =
				_objectDefinitionLocalService.
					fetchObjectDefinitionByExternalReferenceCode(
						"L_AI_HUB_NOTIFICATION_SETTING", companyId);

			if (objectDefinition == null) {
				return null;
			}

			return _objectEntryLocalService.fetchObjectEntry(
				"ai-hub-notification-setting-" + accountEntryId, 0,
				objectDefinition.getObjectDefinitionId());
		}
		catch (Exception exception) {
			_log.error(
				StringBundler.concat(
					"Unable to fetch AI Hub notification setting for company ",
					companyId, " and account ", accountEntryId),
				exception);

			return null;
		}
	}

	private List<String> _getAdminEmailAddresses(
			long companyId, long accountEntryId)
		throws Exception {

		Role role = _roleLocalService.fetchRole(
			companyId,
			AccountRoleConstants.REQUIRED_ROLE_NAME_ACCOUNT_ADMINISTRATOR);

		AccountEntry accountEntry = _accountEntryLocalService.fetchAccountEntry(
			accountEntryId);

		if ((role == null) || (accountEntry == null)) {
			return Collections.emptyList();
		}

		List<String> emailAddresses = new ArrayList<>();

		for (UserGroupRole userGroupRole :
				_userGroupRoleLocalService.getUserGroupRolesByGroupAndRole(
					accountEntry.getAccountEntryGroupId(), role.getRoleId())) {

			User user = userGroupRole.getUser();

			emailAddresses.add(user.getEmailAddress());
		}

		return emailAddresses;
	}

	private List<String> _getEmailAddresses(
			long companyId, long accountEntryId, ObjectEntry objectEntry)
		throws Exception {

		List<String> emailAddresses = _getAdminEmailAddresses(
			companyId, accountEntryId);

		if (objectEntry == null) {
			return emailAddresses;
		}

		String additionalEmailAddresses = MapUtil.getString(
			objectEntry.getValues(), "additionalEmailAddresses");

		for (String emailAddress :
				StringUtil.split(additionalEmailAddresses, ',')) {

			String trimmed = emailAddress.trim();

			if (Validator.isNotNull(trimmed) &&
				!emailAddresses.contains(trimmed)) {

				emailAddresses.add(trimmed);
			}
		}

		return emailAddresses;
	}

	private List<String> _getNotificationTypes(ObjectEntry objectEntry) {
		if (objectEntry == null) {
			return _defaultNotificationTypes;
		}

		String notificationTypes = MapUtil.getString(
			objectEntry.getValues(), "notificationTypes");

		if (Validator.isNull(notificationTypes)) {
			return Collections.emptyList();
		}

		return ListUtil.fromString(
			notificationTypes, StringPool.COMMA_AND_SPACE);
	}

	private Map<String, Object> _getTermValues(
		String emailAddress, AuditMessage auditMessage) {

		long companyId = auditMessage.getCompanyId();

		JSONObject additionalInfoJSONObject = auditMessage.getAdditionalInfo();

		// Not sure if these are provided or we need to implement logic to
		// classify.

		return HashMapBuilder.<String, Object>put(
			"[%AGENT_CLASS_PK%]", auditMessage.getClassPK()
		).put(
			"[%ALERT_TRIGGER%]",
			additionalInfoJSONObject.getString("alertTrigger")
		).put(
			"[%COMPANY_ID%]", String.valueOf(companyId)
		).put(
			"[%FROM_ADDRESS%]",
			PrefsPropsUtil.getString(
				companyId, PropsKeys.ADMIN_EMAIL_FROM_ADDRESS)
		).put(
			"[%FROM_NAME%]",
			PrefsPropsUtil.getString(companyId, PropsKeys.ADMIN_EMAIL_FROM_NAME)
		).put(
			"[%INCIDENT_SEVERITY%]",
			additionalInfoJSONObject.getString("severity")
		).put(
			"[%RECIPIENT_USER_ID%]",
			() -> {
				User user = _userLocalService.fetchUserByEmailAddress(
					companyId, emailAddress);

				if (user == null) {
					return null;
				}

				return String.valueOf(user.getUserId());
			}
		).put(
			"[%TIMESTAMP%]",
			() -> {
				DateFormat dateFormat = DateUtil.getISO8601Format();

				return dateFormat.format(auditMessage.getTimestamp());
			}
		).put(
			"[%TO%]", emailAddress
		).put(
			"[%TRACE_ID%]", additionalInfoJSONObject.getString("traceId")
		).put(
			"[%USE_ID%]", additionalInfoJSONObject.getString("useId")
		).build();
	}

	private boolean _isSeverityEnabled(
		ObjectEntry objectEntry, String severity) {

		if (objectEntry == null) {
			return severity.equals(NotificationConstants.SEVERITY_CRITICAL);
		}

		String notificationSeverities = MapUtil.getString(
			objectEntry.getValues(), "notificationSeverities");

		List<String> severities = ListUtil.fromString(
			notificationSeverities, StringPool.COMMA_AND_SPACE);

		return severities.contains(severity);
	}

	private void _sendNotification(
			String type, String emailAddress,
			NotificationTemplate notificationTemplate,
			AuditMessage auditMessage)
		throws Exception {

		NotificationType notificationType =
			_notificationTypeServiceTracker.getNotificationType(type);

		notificationType.sendNotification(
			new NotificationContextBuilder(
			).className(
				auditMessage.getClassName()
			).classPK(
				GetterUtil.getLong(auditMessage.getClassPK())
			).companyId(
				auditMessage.getCompanyId()
			).notificationTemplate(
				notificationTemplate
			).termValues(
				_getTermValues(emailAddress, auditMessage)
			).userId(
				_userLocalService.getDefaultUserId(auditMessage.getCompanyId())
			).build());
	}

	private void _sendNotifications(
			AuditMessage auditMessage, long accountEntryId,
			ObjectEntry objectEntry)
		throws Exception {

		long companyId = auditMessage.getCompanyId();

		List<String> emailAddresses = _getEmailAddresses(
			companyId, accountEntryId, objectEntry);

		for (String notificationType : _getNotificationTypes(objectEntry)) {
			String externalReferenceCode =
				NotificationConstants.
					getNotificationTemplateExternalReferenceCode(
						notificationType);

			if (externalReferenceCode == null) {
				if (_log.isWarnEnabled()) {
					_log.warn(
						"No template mapping for notification type \"" +
							notificationType + "\"");
				}

				continue;
			}

			NotificationTemplate notificationTemplate =
				_notificationTemplateLocalService.
					fetchNotificationTemplateByExternalReferenceCode(
						externalReferenceCode, companyId);

			if (notificationTemplate == null) {
				continue;
			}

			for (String emailAddress : emailAddresses) {
				try {
					_sendNotification(
						notificationType, emailAddress, notificationTemplate,
						auditMessage);
				}
				catch (Exception exception) {
					_log.error(
						StringBundler.concat(
							"Unable to send AI guardrail alert to ",
							emailAddress, " via ", notificationType),
						exception);
				}
			}
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		AIHubAlertRoutingMessageListener.class);

	private static final List<String> _defaultNotificationTypes = Arrays.asList(
		NotificationConstants.NOTIFICATION_TYPE_EMAIL,
		NotificationConstants.NOTIFICATION_TYPE_USER_NOTIFICATION);

	@Reference
	private AccountEntryLocalService _accountEntryLocalService;

	@Reference
	private DestinationFactory _destinationFactory;

	private ServiceRegistration<Destination> _destinationServiceRegistration;

	@Reference
	private NotificationTemplateLocalService _notificationTemplateLocalService;

	@Reference
	private NotificationTypeServiceTracker _notificationTypeServiceTracker;

	@Reference
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Reference
	private ObjectEntryLocalService _objectEntryLocalService;

	@Reference
	private RoleLocalService _roleLocalService;

	@Reference
	private UserGroupRoleLocalService _userGroupRoleLocalService;

	@Reference
	private UserLocalService _userLocalService;

}