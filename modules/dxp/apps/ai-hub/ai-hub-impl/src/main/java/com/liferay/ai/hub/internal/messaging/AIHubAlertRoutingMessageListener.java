/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.messaging;

import com.liferay.account.constants.AccountRoleConstants;
import com.liferay.account.model.AccountEntry;
import com.liferay.account.service.AccountEntryLocalService;
import com.liferay.ai.hub.internal.constants.AIHubDestinationNames;
import com.liferay.ai.hub.internal.constants.AIHubPortletKeys;
import com.liferay.portal.kernel.audit.AuditMessage;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.json.JSONUtil;
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
import com.liferay.portal.kernel.model.UserNotificationDeliveryConstants;
import com.liferay.portal.kernel.notifications.NotificationEvent;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.UserGroupRoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.service.UserNotificationEventLocalService;
import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.StringUtil;

import java.util.List;

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

		AccountEntry accountEntry =
			_accountEntryLocalService.fetchAccountEntryByExternalReferenceCode(
				"L_AI_HUB", auditMessage.getCompanyId());

		if (accountEntry == null) {
			_log.error(
				"Unable to dispatch AI guardrail alert because account " +
					"L_AI_HUB is missing: " + auditMessage);

			return;
		}

		Role role = _roleLocalService.fetchRole(
			auditMessage.getCompanyId(),
			AccountRoleConstants.REQUIRED_ROLE_NAME_ACCOUNT_ADMINISTRATOR);

		if (role == null) {
			return;
		}

		List<UserGroupRole> userGroupRoles =
			_userGroupRoleLocalService.getUserGroupRolesByGroupAndRole(
				accountEntry.getAccountEntryGroupId(), role.getRoleId());

		for (UserGroupRole userGroupRole : userGroupRoles) {
			User user = _userLocalService.fetchUser(userGroupRole.getUserId());

			if (user != null) {
				_sendUserNotificationEvent(auditMessage, user);
			}
		}
	}

	private void _sendUserNotificationEvent(
			AuditMessage auditMessage, User user)
		throws Exception {

		JSONObject additionalInfoJSONObject = auditMessage.getAdditionalInfo();

		NotificationEvent notificationEvent = new NotificationEvent(
			System.currentTimeMillis(),
			AIHubPortletKeys.AI_HUB_ALERT_NOTIFICATION,
			JSONUtil.put(
				"agentExternalReferenceCode",
				additionalInfoJSONObject.getString(
					"agentDefinitionExternalReferenceCode")
			).put(
				"classPK", auditMessage.getClassPK()
			).put(
				"guardrailType",
				StringUtil.toLowerCase(
					additionalInfoJSONObject.getString("guardrailType"))
			));

		notificationEvent.setDeliveryRequired(0);
		notificationEvent.setDeliveryType(
			UserNotificationDeliveryConstants.TYPE_WEBSITE);

		_userNotificationEventLocalService.addUserNotificationEvent(
			user.getUserId(), true, false, notificationEvent);
	}

	private static final Log _log = LogFactoryUtil.getLog(
		AIHubAlertRoutingMessageListener.class);

	@Reference
	private AccountEntryLocalService _accountEntryLocalService;

	@Reference
	private DestinationFactory _destinationFactory;

	private ServiceRegistration<Destination> _destinationServiceRegistration;

	@Reference
	private RoleLocalService _roleLocalService;

	@Reference
	private UserGroupRoleLocalService _userGroupRoleLocalService;

	@Reference
	private UserLocalService _userLocalService;

	@Reference
	private UserNotificationEventLocalService
		_userNotificationEventLocalService;

}