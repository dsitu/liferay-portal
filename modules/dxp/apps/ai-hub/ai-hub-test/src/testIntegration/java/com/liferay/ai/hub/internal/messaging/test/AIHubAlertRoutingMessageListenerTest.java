/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.messaging.test;

import com.liferay.account.constants.AccountConstants;
import com.liferay.account.constants.AccountRoleConstants;
import com.liferay.account.model.AccountEntry;
import com.liferay.account.service.AccountEntryLocalService;
import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.notification.model.NotificationQueueEntry;
import com.liferay.notification.model.NotificationTemplate;
import com.liferay.notification.service.NotificationQueueEntryLocalService;
import com.liferay.notification.service.NotificationTemplateLocalService;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectEntry;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.portal.kernel.audit.AuditMessage;
import com.liferay.portal.kernel.audit.AuditRouter;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalThreadLocal;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.GroupLocalServiceUtil;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.UserGroupRoleLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.search.test.util.IdempotentRetryAssert;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;
import com.liferay.site.initializer.SiteInitializer;
import com.liferay.site.initializer.SiteInitializerRegistry;

import java.io.Serializable;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Danny Situ
 */
@FeatureFlag("LPD-62272")
@RunWith(Arquillian.class)
public class AIHubAlertRoutingMessageListenerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@BeforeClass
	public static void setUpClass() throws Exception {
		_originalPermissionChecker =
			PermissionThreadLocal.getPermissionChecker();
		_originalName = PrincipalThreadLocal.getName();

		PermissionThreadLocal.setPermissionChecker(
			PermissionCheckerFactoryUtil.create(TestPropsValues.getUser()));
		PrincipalThreadLocal.setName(TestPropsValues.getUserId());
		ServiceContextThreadLocal.pushServiceContext(
			ServiceContextTestUtil.getServiceContext(
				TestPropsValues.getGroupId(), TestPropsValues.getUserId()));

		SiteInitializer siteInitializer =
			_siteInitializerRegistry.getSiteInitializer(
				"com.liferay.ai.hub.site.initializer");

		siteInitializer.initialize(TestPropsValues.getGroupId());
	}

	@AfterClass
	public static void tearDownClass() {
		PermissionThreadLocal.setPermissionChecker(_originalPermissionChecker);
		PrincipalThreadLocal.setName(_originalName);
		ServiceContextThreadLocal.popServiceContext();
	}

	@Before
	public void setUp() throws Exception {
		_user = UserTestUtil.addUser();

		_group = GroupLocalServiceUtil.getGroup(
			_user.getCompanyId(), GroupConstants.GUEST);

		_accountEntry = _accountEntryLocalService.addAccountEntry(
			null, _user.getUserId(),
			AccountConstants.PARENT_ACCOUNT_ENTRY_ID_DEFAULT,
			RandomTestUtil.randomString(), RandomTestUtil.randomString(), null,
			RandomTestUtil.randomString() + "@example.com", null, null,
			AccountConstants.ACCOUNT_ENTRY_TYPE_BUSINESS,
			WorkflowConstants.STATUS_APPROVED,
			ServiceContextTestUtil.getServiceContext(
				_group.getGroupId(), _user.getUserId()));

		Role role = _roleLocalService.getRole(
			TestPropsValues.getCompanyId(),
			AccountRoleConstants.REQUIRED_ROLE_NAME_ACCOUNT_ADMINISTRATOR);

		_userGroupRoleLocalService.addUserGroupRole(
			_user.getUserId(), _accountEntry.getAccountEntryGroupId(),
			role.getRoleId());
	}

	@After
	public void tearDown() throws Exception {
		if (_notificationSettingObjectEntry != null) {
			_objectEntryLocalService.deleteObjectEntry(
				_notificationSettingObjectEntry);
		}

		if (_user != null) {
			_userLocalService.deleteUser(_user);
		}

		if (_accountEntry != null) {
			_accountEntryLocalService.deleteAccountEntry(_accountEntry);
		}
	}

	@Test
	public void testNotificationIsSent() throws Exception {
		NotificationTemplate notificationTemplate =
			_notificationTemplateLocalService.
				fetchNotificationTemplateByExternalReferenceCode(
					"L_AI_HUB_ALERT_EMAIL_TEMPLATE",
					TestPropsValues.getCompanyId());

		Assert.assertNotNull(notificationTemplate);

		ObjectDefinition objectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_AI_HUB_NOTIFICATION_SETTING",
					TestPropsValues.getCompanyId());

		Assert.assertNotNull(objectDefinition);

		_notificationSettingObjectEntry =
			_objectEntryLocalService.addOrUpdateObjectEntry(
				"ai-hub-notification-setting-" +
					_accountEntry.getAccountEntryId(),
				0L, _user.getUserId(), objectDefinition.getObjectDefinitionId(),
				0L,
				HashMapBuilder.<String, Serializable>put(
					"additionalEmailAddresses", ""
				).put(
					"notificationSeverities", "critical"
				).put(
					"notificationTypes", "email, userNotification"
				).put(
					"r_accountToNotificationSettings_accountEntryId",
					_accountEntry.getAccountEntryId()
				).build(),
				ServiceContextTestUtil.getServiceContext(
					_group.getGroupId(), _user.getUserId()));

		Date date = new Date();

		JSONObject additionalInfoJSONObject = JSONFactoryUtil.createJSONObject(
		).put(
			"accountEntryId", _accountEntry.getAccountEntryId()
		).put(
			"alertTrigger", "PII_POST_MASKING"
		).put(
			"severity", "critical"
		).put(
			"traceId", "test-trace"
		).put(
			"useId", "test-use"
		);

		AuditMessage auditMessage = new AuditMessage(
			"AI_GUARDRAIL_ALERT", TestPropsValues.getCompanyId(), 0L,
			_user.getUserId(), _user.getFullName(), "com.liferay.ai.hub.Agent",
			String.valueOf(RandomTestUtil.randomLong()), "Test alert", date,
			additionalInfoJSONObject);

		_auditRouter.route(auditMessage);

		IdempotentRetryAssert.retryAssert(
			10, TimeUnit.SECONDS,
			() -> {
				List<NotificationQueueEntry> notificationQueueEntries =
					_findRecentNotificationQueueEntries(
						notificationTemplate, date);

				Assert.assertFalse(notificationQueueEntries.isEmpty());

				return null;
			});
	}

	private List<NotificationQueueEntry> _findRecentNotificationQueueEntries(
		NotificationTemplate notificationTemplate, Date date) {

		DynamicQuery dynamicQuery =
			_notificationQueueEntryLocalService.dynamicQuery();

		dynamicQuery.add(
			RestrictionsFactoryUtil.eq(
				"notificationTemplateId",
				notificationTemplate.getNotificationTemplateId()));
		dynamicQuery.add(RestrictionsFactoryUtil.ge("createDate", date));

		return _notificationQueueEntryLocalService.dynamicQuery(dynamicQuery);
	}

	private static String _originalName;
	private static PermissionChecker _originalPermissionChecker;

	@Inject
	private static SiteInitializerRegistry _siteInitializerRegistry;

	private AccountEntry _accountEntry;

	@Inject
	private AccountEntryLocalService _accountEntryLocalService;

	@Inject
	private AuditRouter _auditRouter;

	private Group _group;

	@Inject
	private NotificationQueueEntryLocalService
		_notificationQueueEntryLocalService;

	private ObjectEntry _notificationSettingObjectEntry;

	@Inject
	private NotificationTemplateLocalService _notificationTemplateLocalService;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private RoleLocalService _roleLocalService;

	private User _user;

	@Inject
	private UserGroupRoleLocalService _userGroupRoleLocalService;

	@Inject
	private UserLocalService _userLocalService;

}