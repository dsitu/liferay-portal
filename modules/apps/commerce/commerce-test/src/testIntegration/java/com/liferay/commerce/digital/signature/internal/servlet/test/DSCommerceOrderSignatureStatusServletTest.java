/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.commerce.digital.signature.internal.servlet.test;

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
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.log.LogCapture;
import com.liferay.portal.test.log.LoggerTestUtil;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * @author Brian I. Kim
 */
@RunWith(Arquillian.class)
public class DSCommerceOrderSignatureStatusServletTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		CommerceOrderAttachmentTestUtil.initialize(getClass());

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
					"embeddedViewEnabled", true
				).put(
					"enabled", true
				).put(
					"environment", "sandbox"
				).put(
					"integrationKey", RandomTestUtil.randomString()
				).put(
					"rsaPrivateKey", RandomTestUtil.randomString()
				).put(
					"siteSettingsStrategy", "always-inherit"
				).build());

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

		CommerceOrder commerceOrder =
			_commerceOrderLocalService.addCommerceOrder(
				TestPropsValues.getUserId(), commerceChannel.getGroupId(),
				accountEntry.getAccountEntryId(), commerceCurrency.getCode(),
				0);

		_commerceOrderAttachment =
			_commerceOrderAttachmentLocalService.addCommerceOrderAttachment(
				RandomTestUtil.randomString(), TestPropsValues.getUserId(),
				commerceOrder.getCommerceOrderId(), RandomTestUtil.nextDouble(),
				false, RandomTestUtil.randomString(), "invoice",
				RandomTestUtil.randomString() + ".pdf",
				new ByteArrayInputStream(RandomTestUtil.randomBytes()));
	}

	@After
	public void tearDown() throws Exception {
		_companyConfigurationTemporarySwapper.close();
	}

	@Test
	public void testDoGet() throws Exception {
		_assertNotFound(
			_commerceOrderAttachment.getCommerceOrderAttachmentId(), null);

		User user = TestPropsValues.getUser();

		_assertNotFound(RandomTestUtil.randomLong(), user);

		Assert.assertEquals(
			"{}",
			_getContentAsString(
				_commerceOrderAttachment.getCommerceOrderAttachmentId(), user));

		DSRequest dsRequest1 = _addDSRequest(
			DSRequestConstants.STATUS_VOIDED, user);
		DSRequest dsRequest2 = _addDSRequest(
			DSRequestConstants.STATUS_SENT, user);

		JSONObject jsonObject = _jsonFactory.createJSONObject(
			_getContentAsString(
				_commerceOrderAttachment.getCommerceOrderAttachmentId(), user));

		Assert.assertEquals(
			dsRequest2.getProviderRequestId(),
			jsonObject.getString("providerRequestId"));
		Assert.assertEquals(
			DSRequestConstants.STATUS_SENT,
			jsonObject.getString("requestStatus"));

		JSONArray historyJSONArray = jsonObject.getJSONArray("history");

		Assert.assertEquals(
			historyJSONArray.toString(), 1, historyJSONArray.length());

		JSONObject historyJSONObject = historyJSONArray.getJSONObject(0);

		Assert.assertEquals(
			dsRequest1.getProviderRequestId(),
			historyJSONObject.getString("providerRequestId"));
		Assert.assertEquals(
			DSRequestConstants.STATUS_VOIDED,
			historyJSONObject.getString("requestStatus"));

		JSONArray recipientsJSONArray = jsonObject.getJSONArray("recipients");

		Assert.assertEquals(
			recipientsJSONArray.toString(), 1, recipientsJSONArray.length());

		JSONObject recipientJSONObject = recipientsJSONArray.getJSONObject(0);

		Assert.assertEquals(
			user.getEmailAddress(),
			recipientJSONObject.getString("emailAddress"));
		Assert.assertEquals(
			DSRequestRecipientConstants.STATUS_SENT,
			recipientJSONObject.getString("requestRecipientStatus"));

		_assertNotFound(
			_commerceOrderAttachment.getCommerceOrderAttachmentId(),
			UserTestUtil.addUser());
	}

	private DSRequest _addDSRequest(String status, User user) throws Exception {
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
		dsEnvelope.setStatus(status);

		return ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			_group.getCompanyId(), _group.getGroupId(), user.getUserId(),
			dsEnvelope, new long[] {_commerceOrderAttachment.getFileEntryId()});
	}

	private void _assertNotFound(long commerceOrderAttachmentId, User user)
		throws Exception {

		try (LogCapture logCapture = LoggerTestUtil.configureLog4JLogger(
				"com.liferay.commerce.digital.signature.internal.servlet." +
					"DSCommerceOrderSignatureStatusServlet",
				LoggerTestUtil.OFF)) {

			MockHttpServletResponse mockHttpServletResponse =
				_getMockHttpServletResponse(commerceOrderAttachmentId, user);

			Assert.assertEquals(
				HttpServletResponse.SC_NOT_FOUND,
				mockHttpServletResponse.getStatus());
		}
	}

	private String _getContentAsString(
			long commerceOrderAttachmentId, User user)
		throws Exception {

		MockHttpServletResponse mockHttpServletResponse =
			_getMockHttpServletResponse(commerceOrderAttachmentId, user);

		return mockHttpServletResponse.getContentAsString();
	}

	private MockHttpServletResponse _getMockHttpServletResponse(
			long commerceOrderAttachmentId, User user)
		throws Exception {

		MockHttpServletResponse mockHttpServletResponse =
			new MockHttpServletResponse();

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.setAttribute(
			WebKeys.COMPANY_ID, _group.getCompanyId());

		if (user != null) {
			mockHttpServletRequest.setAttribute(WebKeys.USER, user);
		}

		mockHttpServletRequest.setParameter(
			"commerceOrderAttachmentId",
			String.valueOf(commerceOrderAttachmentId));

		_servlet.service(mockHttpServletRequest, mockHttpServletResponse);

		return mockHttpServletResponse;
	}

	private CommerceOrderAttachment _commerceOrderAttachment;

	@Inject
	private CommerceOrderAttachmentLocalService
		_commerceOrderAttachmentLocalService;

	@Inject
	private CommerceOrderLocalService _commerceOrderLocalService;

	private CompanyConfigurationTemporarySwapper
		_companyConfigurationTemporarySwapper;

	@Inject
	private DSRequestManager _dsRequestManager;

	private Group _group;

	@Inject
	private JSONFactory _jsonFactory;

	@Inject(
		filter = "osgi.http.whiteboard.servlet.name=com.liferay.commerce.digital.signature.internal.servlet.DSCommerceOrderSignatureStatusServlet"
	)
	private Servlet _servlet;

}