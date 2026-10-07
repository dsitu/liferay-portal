/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.portlet.action.test;

import com.liferay.account.model.AccountEntry;
import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.commerce.account.test.util.CommerceAccountTestUtil;
import com.liferay.commerce.currency.model.CommerceCurrency;
import com.liferay.commerce.currency.test.util.CommerceCurrencyTestUtil;
import com.liferay.commerce.model.CommerceOrder;
import com.liferay.commerce.model.CommerceOrderAttachment;
import com.liferay.commerce.product.model.CommerceChannel;
import com.liferay.commerce.service.CommerceOrderAttachmentLocalService;
import com.liferay.commerce.service.CommerceOrderLocalService;
import com.liferay.commerce.test.util.CommerceOrderAttachmentTestUtil;
import com.liferay.commerce.test.util.CommerceTestUtil;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.constants.DSRequestConstants;
import com.liferay.digital.signature.constants.DSRequestRecipientConstants;
import com.liferay.digital.signature.manager.DSRequestManager;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.portlet.MockLiferayResourceRequest;
import com.liferay.portal.kernel.test.portlet.MockLiferayResourceResponse;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import jakarta.portlet.PortletException;

import java.io.ByteArrayInputStream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Brian I. Kim
 */
@RunWith(Arquillian.class)
public class AddDSRequestMVCResourceCommandTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		CommerceOrderAttachmentTestUtil.initialize(getClass());

		_saveDigitalSignatureConfiguration(true);

		_group = GroupTestUtil.addGroup();

		CommerceCurrency commerceCurrency =
			CommerceCurrencyTestUtil.addCommerceCurrency(_group.getCompanyId());

		CommerceChannel commerceChannel = CommerceTestUtil.addCommerceChannel(
			_group.getGroupId(), commerceCurrency.getCode());

		AccountEntry accountEntry =
			CommerceAccountTestUtil.addPersonAccountEntry(
				TestPropsValues.getUserId(),
				ServiceContextTestUtil.getServiceContext(
					_group.getGroupId(), TestPropsValues.getUserId()));

		_commerceOrder = _commerceOrderLocalService.addCommerceOrder(
			TestPropsValues.getUserId(), commerceChannel.getGroupId(),
			accountEntry.getAccountEntryId(), commerceCurrency.getCode(), 0);

		_commerceOrderAttachment =
			_commerceOrderAttachmentLocalService.addCommerceOrderAttachment(
				RandomTestUtil.randomString(), TestPropsValues.getUserId(),
				_commerceOrder.getCommerceOrderId(),
				RandomTestUtil.nextDouble(), false,
				RandomTestUtil.randomString(), "invoice",
				RandomTestUtil.randomString() + ".pdf",
				new ByteArrayInputStream(RandomTestUtil.randomBytes()));
	}

	@After
	public void tearDown() throws Exception {
		_companyConfigurationTemporarySwapper.close();
	}

	@Test
	public void testServeResource() throws Exception {
		try {
			_serveResource(UserTestUtil.addUser(), 0, 0);

			Assert.fail();
		}
		catch (PortletException portletException) {
			Assert.assertTrue(
				portletException.getCause() instanceof
					PrincipalException.MustHavePermission);
		}

		_saveDigitalSignatureConfiguration(false);

		_testServeResource(
			"Digital signature is not enabled for order " +
				_commerceOrder.getCommerceOrderId(),
			0, 0);

		_saveDigitalSignatureConfiguration(true);

		_testServeResource(
			"The number of days to warn signers must be fewer than the " +
				"number of days until expiration.",
			1, 1);

		User user1 = UserTestUtil.addUser();

		_userLocalService.updateStatus(
			user1.getUserId(), WorkflowConstants.STATUS_INACTIVE,
			ServiceContextTestUtil.getServiceContext());

		_testServeResource(
			StringBundler.concat(
				"User ", user1.getUserId(), " is not allowed to sign order ",
				_commerceOrder.getCommerceOrderId()),
			0, 0, user1.getUserId());

		User user2 = UserTestUtil.addUser();
		User user3 = UserTestUtil.addUser();

		_testServeResource(
			"Only one internal countersigner is allowed", 0, 0,
			user2.getUserId(), user3.getUserId());

		_testServeResource("At least one recipient is required", 0, 0);

		_addDSRequest(TestPropsValues.getUser());

		_testServeResource(
			StringBundler.concat(
				"Commerce order attachment ",
				_commerceOrderAttachment.getCommerceOrderAttachmentId(),
				" already has an active or completed signature request"),
			0, 0, TestPropsValues.getUserId());
	}

	private void _addDSRequest(User user) throws Exception {
		DSEnvelope dsEnvelope = new DSEnvelope();

		dsEnvelope.setDSEnvelopeId(RandomTestUtil.randomString());

		DSRecipient dsRecipient = new DSRecipient();

		dsRecipient.setDSRecipientId("1");
		dsRecipient.setEmailAddress(user.getEmailAddress());
		dsRecipient.setName(user.getFullName());
		dsRecipient.setRoutingOrder(1);
		dsRecipient.setStatus(DSRequestRecipientConstants.STATUS_SENT);

		dsEnvelope.setDSRecipients(ListUtil.fromArray(dsRecipient));

		dsEnvelope.setEmailSubject(RandomTestUtil.randomString());
		dsEnvelope.setStatus(DSRequestConstants.STATUS_SENT);

		ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			_group.getCompanyId(), _group.getGroupId(), user.getUserId(),
			dsEnvelope, new long[] {_commerceOrderAttachment.getFileEntryId()});
	}

	private void _saveDigitalSignatureConfiguration(boolean enabled)
		throws Exception {

		if (_companyConfigurationTemporarySwapper != null) {
			_companyConfigurationTemporarySwapper.close();
		}

		_companyConfigurationTemporarySwapper =
			new CompanyConfigurationTemporarySwapper(
				TestPropsValues.getCompanyId(),
				DigitalSignatureConfiguration.class.getName(),
				HashMapDictionaryBuilder.<String, Object>put(
					"accountBaseURI", "https://demo.docusign.net/restapi"
				).put(
					"apiAccountId", RandomTestUtil.randomString()
				).put(
					"apiUsername", RandomTestUtil.randomString()
				).put(
					"enabled", enabled
				).put(
					"enableEmbeddedView", true
				).put(
					"environment", "sandbox"
				).put(
					"integrationKey", RandomTestUtil.randomString()
				).put(
					"rsaPrivateKey", RandomTestUtil.randomString()
				).put(
					"siteSettingsStrategy", "always-inherit"
				).build());
	}

	private void _serveResource(
			User user, int expireAfter, int expireWarn,
			long... recipientUserIds)
		throws Exception {

		MockLiferayResourceRequest mockLiferayResourceRequest =
			new MockLiferayResourceRequest();

		ThemeDisplay themeDisplay = new ThemeDisplay();

		themeDisplay.setCompany(
			_companyLocalService.getCompany(_group.getCompanyId()));
		themeDisplay.setLocale(LocaleUtil.US);
		themeDisplay.setPermissionChecker(
			PermissionCheckerFactoryUtil.create(user));
		themeDisplay.setUser(user);

		mockLiferayResourceRequest.setAttribute(
			WebKeys.THEME_DISPLAY, themeDisplay);

		mockLiferayResourceRequest.addParameter(
			"commerceOrderAttachmentId",
			String.valueOf(
				_commerceOrderAttachment.getCommerceOrderAttachmentId()));
		mockLiferayResourceRequest.addParameter(
			"expireAfter", String.valueOf(expireAfter));
		mockLiferayResourceRequest.addParameter(
			"expireWarn", String.valueOf(expireWarn));

		for (long recipientUserId : recipientUserIds) {
			mockLiferayResourceRequest.addParameter(
				"recipientUserIds", String.valueOf(recipientUserId));
		}

		_mvcResourceCommand.serveResource(
			mockLiferayResourceRequest, new MockLiferayResourceResponse());
	}

	private void _testServeResource(
			String expectedMessage, int expireAfter, int expireWarn,
			long... recipientUserIds)
		throws Exception {

		try {
			_serveResource(
				TestPropsValues.getUser(), expireAfter, expireWarn,
				recipientUserIds);

			Assert.fail();
		}
		catch (PortletException portletException) {
			Throwable throwable = portletException.getCause();

			Assert.assertEquals(expectedMessage, throwable.getMessage());
		}
	}

	private CommerceOrder _commerceOrder;
	private CommerceOrderAttachment _commerceOrderAttachment;

	@Inject
	private CommerceOrderAttachmentLocalService
		_commerceOrderAttachmentLocalService;

	@Inject
	private CommerceOrderLocalService _commerceOrderLocalService;

	private CompanyConfigurationTemporarySwapper
		_companyConfigurationTemporarySwapper;

	@Inject
	private CompanyLocalService _companyLocalService;

	@Inject
	private DSRequestManager _dsRequestManager;

	private Group _group;

	@Inject(filter = "mvc.command.name=/commerce_order/add_ds_request")
	private MVCResourceCommand _mvcResourceCommand;

	@Inject
	private UserLocalService _userLocalService;

}