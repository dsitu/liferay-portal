/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.digital.signature.internal.request.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.digital.signature.configuration.DigitalSignatureConfiguration;
import com.liferay.digital.signature.model.DSDocument;
import com.liferay.digital.signature.model.DSEnvelope;
import com.liferay.digital.signature.model.DSRecipient;
import com.liferay.digital.signature.model.DSRequest;
import com.liferay.digital.signature.request.DSRequestManager;
import com.liferay.object.constants.ObjectDefinitionConstants;
import com.liferay.object.model.ObjectDefinition;
import com.liferay.object.model.ObjectField;
import com.liferay.object.model.ObjectRelationship;
import com.liferay.object.rest.filter.factory.FilterFactory;
import com.liferay.object.service.ObjectDefinitionLocalService;
import com.liferay.object.service.ObjectEntryLocalService;
import com.liferay.object.service.ObjectFieldLocalService;
import com.liferay.object.service.ObjectRelationshipLocalService;
import com.liferay.petra.sql.dsl.expression.Predicate;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.configuration.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionCheckerFactoryUtil;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.test.util.TestPropsValues;
import com.liferay.portal.kernel.test.util.UserTestUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.HttpComponentsUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.SetUtil;
import com.liferay.portal.kernel.util.URLCodec;
import com.liferay.portal.test.rule.Inject;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.test.rule.PermissionCheckerMethodTestRule;

import java.io.Serializable;

import java.time.LocalDateTime;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class DSRequestManagerTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new AggregateTestRule(
			new LiferayIntegrationTestRule(),
			PermissionCheckerMethodTestRule.INSTANCE);

	@Before
	public void setUp() throws Exception {
		_configurationProvider.saveCompanyConfiguration(
			DigitalSignatureConfiguration.class, TestPropsValues.getCompanyId(),
			HashMapDictionaryBuilder.<String, Object>put(
				"accountBaseURI", "https://demo.docusign.net/restapi"
			).put(
				"apiAccountId", RandomTestUtil.randomString()
			).put(
				"apiUsername", RandomTestUtil.randomString()
			).put(
				"enabled", true
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

		_group = GroupTestUtil.addGroup();
	}

	@After
	public void tearDown() throws Exception {
		_configurationProvider.deleteCompanyConfiguration(
			DigitalSignatureConfiguration.class,
			TestPropsValues.getCompanyId());
	}

	@Test
	public void testAddDSRequestWhenEmbeddedViewIsDisabled() throws Exception {
		_configurationProvider.saveCompanyConfiguration(
			DigitalSignatureConfiguration.class, TestPropsValues.getCompanyId(),
			HashMapDictionaryBuilder.<String, Object>put(
				"enabled", true
			).put(
				"enableEmbeddedView", false
			).put(
				"siteSettingsStrategy", "always-inherit"
			).build());

		long fileEntryId = RandomTestUtil.randomInt();

		try {
			_dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), _group.getGroupId(),
				TestPropsValues.getUserId(), _createDSEnvelope(fileEntryId),
				new long[] {fileEntryId});

			Assert.fail();
		}
		catch (PortalException portalException) {
			String message = portalException.getMessage();

			Assert.assertTrue(message, message.contains("not enabled"));
		}
	}

	@Test
	public void testAddDSRequestWhenFileEntryHasActiveRequest()
		throws Exception {

		long fileEntryId = RandomTestUtil.randomInt();

		ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			TestPropsValues.getCompanyId(), _group.getGroupId(),
			TestPropsValues.getUserId(), _createDSEnvelope(fileEntryId),
			new long[] {fileEntryId});

		try {
			_dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), _group.getGroupId(),
				TestPropsValues.getUserId(), _createDSEnvelope(fileEntryId),
				new long[] {fileEntryId});

			Assert.fail();
		}
		catch (PortalException portalException) {
			String message = portalException.getMessage();

			Assert.assertTrue(
				message, message.contains("already has a signature request"));
		}
	}

	@Test
	public void testAddDSRequests() throws Exception {
		long companyId = TestPropsValues.getCompanyId();

		long fileEntryId = RandomTestUtil.randomInt();

		DSRequest dsRequest = ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			companyId, _group.getGroupId(), TestPropsValues.getUserId(),
			_createDSEnvelope(fileEntryId), new long[] {fileEntryId});

		Assert.assertEquals(
			Collections.singletonList(fileEntryId),
			dsRequest.getFileEntryIds());
		Assert.assertEquals(
			"env-" + fileEntryId, dsRequest.getProviderRequestId());
		Assert.assertEquals(_group.getGroupId(), dsRequest.getSiteId());

		ObjectDefinition requestObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST", companyId);

		List<Map<String, Serializable>> requestValuesList = _getValuesList(
			companyId, requestObjectDefinition,
			"(fileEntryId eq " + fileEntryId + ")");

		Assert.assertEquals(
			requestValuesList.toString(), 1, requestValuesList.size());

		Map<String, Serializable> requestValues = requestValuesList.get(0);

		Assert.assertEquals("sent", requestValues.get("requestStatus"));
		Assert.assertEquals(
			"env-" + fileEntryId, requestValues.get("providerRequestId"));

		ObjectDefinition recipientObjectDefinition =
			_objectDefinitionLocalService.
				fetchObjectDefinitionByExternalReferenceCode(
					"L_DS_REQUEST_RECIPIENT", companyId);

		ObjectRelationship objectRelationship =
			_objectRelationshipLocalService.fetchObjectRelationship(
				requestObjectDefinition.getObjectDefinitionId(),
				"dsRequestToDSRequestRecipients");

		ObjectField objectField = _objectFieldLocalService.getObjectField(
			objectRelationship.getObjectFieldId2());

		long requestId = GetterUtil.getLong(
			requestValues.get(requestObjectDefinition.getPKObjectFieldName()));

		List<Map<String, Serializable>> recipientValuesList = _getValuesList(
			companyId, recipientObjectDefinition,
			StringBundler.concat(
				"(", objectField.getName(), " eq '", requestId, "')"));

		Assert.assertEquals(
			recipientValuesList.toString(), 2, recipientValuesList.size());

		Set<String> emailAddresses = new HashSet<>();

		for (Map<String, Serializable> recipientValues : recipientValuesList) {
			emailAddresses.add(
				GetterUtil.getString(recipientValues.get("emailAddress")));
		}

		Assert.assertTrue(
			emailAddresses.toString(),
			emailAddresses.contains("ray.chen@liferay.com"));
		Assert.assertTrue(
			emailAddresses.toString(),
			emailAddresses.contains("mei.lin@liferay.com"));
	}

	@Test
	public void testContainsPermission() throws Exception {
		_user = UserTestUtil.addUser();

		DSRequest dsRequest = _addDSRequest(_user.getEmailAddress(), "sent");

		PermissionChecker permissionChecker =
			PermissionCheckerFactoryUtil.create(_user);

		Assert.assertTrue(
			_dsRequestManager.containsPermission(
				permissionChecker, dsRequest, ActionKeys.UPDATE));
		Assert.assertTrue(
			_dsRequestManager.containsPermission(
				permissionChecker, dsRequest, ActionKeys.VIEW));

		_otherUser = UserTestUtil.addUser();

		permissionChecker = PermissionCheckerFactoryUtil.create(_otherUser);

		Assert.assertFalse(
			_dsRequestManager.containsPermission(
				permissionChecker, dsRequest, ActionKeys.UPDATE));
		Assert.assertFalse(
			_dsRequestManager.containsPermission(
				permissionChecker, dsRequest, ActionKeys.VIEW));
	}

	@Test
	public void testFetchDSRequestByRequestId() throws Exception {
		long fileEntryId = RandomTestUtil.randomInt();

		DSRequest dsRequest = ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			TestPropsValues.getCompanyId(), _group.getGroupId(),
			TestPropsValues.getUserId(), _createDSEnvelope(fileEntryId),
			new long[] {fileEntryId});

		DSRequest requestIdDSRequest = _dsRequestManager.fetchDSRequest(
			dsRequest.getDSRequestId());

		Assert.assertEquals(
			dsRequest.getDSRequestId(), requestIdDSRequest.getDSRequestId());
		Assert.assertEquals("sent", requestIdDSRequest.getStatus());
	}

	@Test
	public void testFetchDSRequestWhenExpirationDateIsPast() throws Exception {
		long fileEntryId = RandomTestUtil.randomInt();

		DSEnvelope dsEnvelope = _createDSEnvelope(fileEntryId);

		dsEnvelope.setExpireLocalDateTime(
			LocalDateTime.now(
			).minusDays(
				1
			));

		DSRequest dsRequest = ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			TestPropsValues.getCompanyId(), _group.getGroupId(),
			TestPropsValues.getUserId(), dsEnvelope, new long[] {fileEntryId});

		dsRequest = _dsRequestManager.fetchDSRequest(
			dsRequest.getDSRequestId());

		Assert.assertEquals("expired", dsRequest.getStatus());
		Assert.assertTrue(dsRequest.isRequestable());
		Assert.assertTrue(dsRequest.isTerminal());
	}

	@Test
	public void testGetLoginURL() throws Exception {
		String path = StringBundler.concat(
			"/web/", RandomTestUtil.randomString(),
			"/manage/-/digital_signature/sign/", RandomTestUtil.randomLong());
		String portalURL = StringBundler.concat(
			"http://", RandomTestUtil.randomString(), ".com");

		String url = ReflectionTestUtil.invoke(
			_dsRequestManager, "_getLoginURL", new Class<?>[] {String.class},
			portalURL + path);

		Assert.assertTrue(
			url,
			url.startsWith(
				StringBundler.concat(
					portalURL, PortalUtil.getPathMain(), "/portal/login?")));
		Assert.assertEquals(
			path,
			URLCodec.decodeURL(
				HttpComponentsUtil.getParameter(url, "redirect", false)));
	}

	@Test
	public void testGetRecipientDSRequests() throws Exception {
		long companyId = TestPropsValues.getCompanyId();

		_user = UserTestUtil.addUser();

		DSRequest dsRequest1 = _addDSRequest(_user.getEmailAddress(), "voided");
		DSRequest dsRequest2 = _addDSRequest(_user.getEmailAddress(), "sent");

		List<DSRequest> dsRequests = _dsRequestManager.getRecipientDSRequests(
			companyId, _user.getUserId(), null, QueryUtil.ALL_POS,
			QueryUtil.ALL_POS);

		Set<Long> dsRequestIds = new HashSet<>();

		for (DSRequest dsRequest : dsRequests) {
			dsRequestIds.add(dsRequest.getDSRequestId());
		}

		Assert.assertEquals(
			SetUtil.fromArray(
				new Long[] {
					dsRequest1.getDSRequestId(), dsRequest2.getDSRequestId()
				}),
			dsRequestIds);

		Assert.assertEquals(
			2,
			_dsRequestManager.getRecipientDSRequestsCount(
				companyId, _user.getUserId(), null));

		dsRequests = _dsRequestManager.getRecipientDSRequests(
			companyId, _user.getUserId(), null, 0, 1);

		Assert.assertEquals(dsRequests.toString(), 1, dsRequests.size());

		Assert.assertEquals(
			1,
			_dsRequestManager.getRecipientDSRequestsCount(
				companyId, _user.getUserId(), dsRequest1.getEmailSubject()));
		Assert.assertEquals(
			0,
			_dsRequestManager.getRecipientDSRequestsCount(
				companyId, _user.getUserId(), RandomTestUtil.randomString()));
	}

	@Test
	public void testSendDSRequestNotificationsWhenRequestIsTerminal()
		throws Exception {

		_user = UserTestUtil.addUser();

		int count = ReflectionTestUtil.invoke(
			_dsRequestManager, "_sendDSRequestNotifications",
			new Class<?>[] {long.class, long.class, DSRequest.class},
			TestPropsValues.getCompanyId(), _group.getGroupId(),
			_addDSRequest(_user.getEmailAddress(), "voided"));

		Assert.assertEquals(0, count);

		count = ReflectionTestUtil.invoke(
			_dsRequestManager, "_sendDSRequestNotifications",
			new Class<?>[] {long.class, long.class, DSRequest.class},
			TestPropsValues.getCompanyId(), _group.getGroupId(),
			_addDSRequest(_user.getEmailAddress(), "sent"));

		Assert.assertEquals(1, count);
	}

	@Test
	public void testSendDSRequestWhenExpirationWarningIsNotBeforeExpiration()
		throws Exception {

		long fileEntryId = RandomTestUtil.randomInt();

		DSEnvelope dsEnvelope = _createDSEnvelope(fileEntryId);

		dsEnvelope.setExpireAfter(5);
		dsEnvelope.setExpireWarn(5);

		try {
			_dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), _group.getGroupId(),
				TestPropsValues.getUserId(), dsEnvelope,
				new long[] {fileEntryId});

			Assert.fail();
		}
		catch (PortalException portalException) {
			String message = portalException.getMessage();

			Assert.assertTrue(message, message.contains("fewer than"));
		}
	}

	@Test
	public void testSendDSRequestWhenFileEntryIdsIsEmpty() throws Exception {
		try {
			_dsRequestManager.addDSRequest(
				TestPropsValues.getCompanyId(), _group.getGroupId(),
				TestPropsValues.getUserId(), new DSEnvelope(), new long[0]);

			Assert.fail();
		}
		catch (PortalException portalException) {
			String message = portalException.getMessage();

			Assert.assertTrue(message, message.contains("at least one"));
		}
	}

	private DSRequest _addDSRequest(String emailAddress, String status)
		throws Exception {

		long fileEntryId = RandomTestUtil.randomInt();

		DSEnvelope dsEnvelope = _createDSEnvelope(fileEntryId);

		List<DSRecipient> dsRecipients = dsEnvelope.getDSRecipients();

		DSRecipient dsRecipient = dsRecipients.get(0);

		dsRecipient.setEmailAddress(emailAddress);

		dsEnvelope.setEmailSubject(RandomTestUtil.randomString());
		dsEnvelope.setStatus(status);

		return ReflectionTestUtil.invoke(
			_dsRequestManager, "_addDSRequest",
			new Class<?>[] {
				long.class, long.class, long.class, DSEnvelope.class,
				long[].class
			},
			TestPropsValues.getCompanyId(), _group.getGroupId(),
			TestPropsValues.getUserId(), dsEnvelope, new long[] {fileEntryId});
	}

	private DSEnvelope _createDSEnvelope(long fileEntryId) {
		DSEnvelope dsEnvelope = new DSEnvelope();

		DSDocument dsDocument = new DSDocument();

		dsDocument.setDSDocumentId(String.valueOf(fileEntryId));

		dsEnvelope.setDSDocuments(ListUtil.fromArray(dsDocument));

		dsEnvelope.setDSEnvelopeId("env-" + fileEntryId);
		dsEnvelope.setDSRecipients(
			ListUtil.fromArray(
				_createDSRecipient("1", "ray.chen@liferay.com", "Ray Chen"),
				_createDSRecipient("2", "mei.lin@liferay.com", "Mei Lin")));
		dsEnvelope.setEmailSubject("Please sign");
		dsEnvelope.setStatus("sent");

		return dsEnvelope;
	}

	private DSRecipient _createDSRecipient(
		String dsRecipientId, String emailAddress, String name) {

		DSRecipient dsRecipient = new DSRecipient();

		dsRecipient.setDSRecipientId(dsRecipientId);
		dsRecipient.setEmailAddress(emailAddress);
		dsRecipient.setName(name);
		dsRecipient.setStatus("sent");

		return dsRecipient;
	}

	private List<Map<String, Serializable>> _getValuesList(
			long companyId, ObjectDefinition objectDefinition,
			String filterString)
		throws Exception {

		return _objectEntryLocalService.getValuesList(
			0, companyId, TestPropsValues.getUserId(),
			objectDefinition.getObjectDefinitionId(),
			_filterFactory.create(filterString, objectDefinition), null,
			QueryUtil.ALL_POS, QueryUtil.ALL_POS, null);
	}

	@Inject
	private ConfigurationProvider _configurationProvider;

	@Inject
	private DSRequestManager _dsRequestManager;

	@Inject(
		filter = "filter.factory.key=" + ObjectDefinitionConstants.STORAGE_TYPE_DEFAULT
	)
	private FilterFactory<Predicate> _filterFactory;

	@DeleteAfterTestRun
	private Group _group;

	@Inject
	private ObjectDefinitionLocalService _objectDefinitionLocalService;

	@Inject
	private ObjectEntryLocalService _objectEntryLocalService;

	@Inject
	private ObjectFieldLocalService _objectFieldLocalService;

	@Inject
	private ObjectRelationshipLocalService _objectRelationshipLocalService;

	@DeleteAfterTestRun
	private User _otherUser;

	@DeleteAfterTestRun
	private User _user;

}