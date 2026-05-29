/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.ai.hub.internal.messaging.test;

import com.liferay.account.constants.AccountConstants;
import com.liferay.account.constants.AccountRoleConstants;
import com.liferay.account.model.AccountEntry;
import com.liferay.account.model.AccountRole;
import com.liferay.account.service.AccountEntryLocalService;
import com.liferay.account.service.AccountEntryUserRelLocalService;
import com.liferay.account.service.AccountRoleLocalService;
import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.audit.AuditMessage;
import com.liferay.portal.kernel.audit.AuditRouter;
import com.liferay.portal.kernel.json.JSONUtil;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.UserNotificationEventLocalService;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.kernel.workflow.WorkflowInstance;
import com.liferay.portal.search.test.util.IdempotentRetryAssert;
import com.liferay.portal.test.rule.FeatureFlag;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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

	@Before
	public void setUp() throws Exception {
		ServiceContextThreadLocal.pushServiceContext(
			ServiceContextTestUtil.getServiceContext(
				TestPropsValues.getGroupId(), TestPropsValues.getUserId()));

		_accountEntry =
			_accountEntryLocalService.fetchAccountEntryByExternalReferenceCode(
				"L_AI_HUB", TestPropsValues.getCompanyId());

		if (_accountEntry == null) {
			_accountEntry = _accountEntryLocalService.addAccountEntry(
				"L_AI_HUB", TestPropsValues.getUserId(),
				AccountConstants.PARENT_ACCOUNT_ENTRY_ID_DEFAULT,
				"Liferay AI Hub", null, null, null, null, null,
				AccountConstants.ACCOUNT_ENTRY_TYPE_BUSINESS,
				WorkflowConstants.STATUS_APPROVED,
				ServiceContextTestUtil.getServiceContext());
		}

		_user1 = UserTestUtil.addUser();

		_accountEntryUserRelLocalService.addAccountEntryUserRel(
			_accountEntry.getAccountEntryId(), _user1.getUserId());

		_user2 = UserTestUtil.addUser();

		_accountEntryUserRelLocalService.addAccountEntryUserRel(
			_accountEntry.getAccountEntryId(), _user2.getUserId());

		_user3 = UserTestUtil.addUser();

		_accountEntryUserRelLocalService.addAccountEntryUserRel(
			_accountEntry.getAccountEntryId(), _user3.getUserId());

		Role role = _roleLocalService.getRole(
			TestPropsValues.getCompanyId(),
			AccountRoleConstants.REQUIRED_ROLE_NAME_ACCOUNT_ADMINISTRATOR);

		AccountRole accountRole =
			_accountRoleLocalService.fetchAccountRoleByRoleId(role.getRoleId());

		_accountRoleLocalService.associateUser(
			_accountEntry.getAccountEntryId(), accountRole.getAccountRoleId(),
			_user1.getUserId());
		_accountRoleLocalService.associateUser(
			_accountEntry.getAccountEntryId(), accountRole.getAccountRoleId(),
			_user2.getUserId());
	}

	@After
	public void tearDown() {
		ServiceContextThreadLocal.popServiceContext();
	}

	@Test
	public void testDoReceive() throws Exception {
		String agentExternalReferenceCode = RandomTestUtil.randomString();

		for (int i = 0; i < 4; i++) {
			_auditRouter.route(
				new AuditMessage(
					0, TestPropsValues.getCompanyId(),
					TestPropsValues.getUserId(), StringPool.BLANK, new Date(),
					0,
					JSONUtil.put(
						"agentDefinitionExternalReferenceCode",
						agentExternalReferenceCode
					).put(
						"guardrailType", "INPUT"
					),
					WorkflowInstance.class.getName(),
					String.valueOf(RandomTestUtil.randomLong()), null,
					_EVENT_TYPE, null));
		}

		Assert.assertEquals(
			0,
			_userNotificationEventLocalService.getUserNotificationEventsCount(
				_user1.getUserId()));

		_auditRouter.route(
			new AuditMessage(
				0, TestPropsValues.getCompanyId(), TestPropsValues.getUserId(),
				StringPool.BLANK, new Date(), 0,
				JSONUtil.put(
					"agentDefinitionExternalReferenceCode",
					agentExternalReferenceCode
				).put(
					"guardrailType", "INPUT"
				),
				WorkflowInstance.class.getName(),
				String.valueOf(RandomTestUtil.randomLong()), null, _EVENT_TYPE,
				null));

		IdempotentRetryAssert.retryAssert(
			10, TimeUnit.SECONDS,
			() -> {
				Assert.assertEquals(
					1,
					_userNotificationEventLocalService.
						getUserNotificationEventsCount(_user1.getUserId()));
				Assert.assertEquals(
					1,
					_userNotificationEventLocalService.
						getUserNotificationEventsCount(_user2.getUserId()));

				return null;
			});

		Assert.assertEquals(
			0,
			_userNotificationEventLocalService.getUserNotificationEventsCount(
				_user3.getUserId()));
	}

	private static final String _EVENT_TYPE = "AI_HUB_GUARDRAIL_ALERT";

	private AccountEntry _accountEntry;

	@Inject
	private AccountEntryLocalService _accountEntryLocalService;

	@Inject
	private AccountEntryUserRelLocalService _accountEntryUserRelLocalService;

	@Inject
	private AccountRoleLocalService _accountRoleLocalService;

	@Inject
	private AuditRouter _auditRouter;

	@Inject
	private RoleLocalService _roleLocalService;

	private User _user1;
	private User _user2;
	private User _user3;

	@Inject
	private UserNotificationEventLocalService
		_userNotificationEventLocalService;

}