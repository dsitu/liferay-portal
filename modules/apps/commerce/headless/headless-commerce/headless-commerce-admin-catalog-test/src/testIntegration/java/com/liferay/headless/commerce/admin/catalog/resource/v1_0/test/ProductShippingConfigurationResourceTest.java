/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.headless.commerce.admin.catalog.resource.v1_0.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.commerce.product.configuration.CProductVersionConfiguration;
import com.liferay.commerce.product.model.CPDefinition;
import com.liferay.commerce.product.model.CommerceCatalog;
import com.liferay.commerce.product.service.CPDefinitionLocalService;
import com.liferay.commerce.product.test.util.CPTestUtil;
import com.liferay.headless.commerce.admin.catalog.client.dto.v1_0.ProductShippingConfiguration;
import com.liferay.portal.configuration.test.util.CompanyConfigurationTemporarySwapper;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.test.rule.DeleteAfterTestRun;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.test.rule.Inject;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Zoltán Takács
 */
@RunWith(Arquillian.class)
public class ProductShippingConfigurationResourceTest
	extends BaseProductShippingConfigurationResourceTestCase {

	@Ignore
	@Override
	@Test
	public void testClientSerDesToDTO() throws Exception {
		super.testClientSerDesToDTO();
	}

	@Ignore
	@Override
	@Test
	public void testClientSerDesToJSON() throws Exception {
		super.testClientSerDesToJSON();
	}

	@Ignore
	@Override
	@Test
	public void testEscapeRegexInStringFields() throws Exception {
		super.testEscapeRegexInStringFields();
	}

	@Ignore
	@Override
	@Test
	public void testGetProductByExternalReferenceCodeShippingConfiguration()
		throws Exception {

		super.testGetProductByExternalReferenceCodeShippingConfiguration();
	}

	@Ignore
	@Override
	@Test
	public void testGetProductIdShippingConfiguration() throws Exception {
		super.testGetProductIdShippingConfiguration();
	}

	@Ignore
	@Override
	@Test
	public void testGraphQLGetProductByExternalReferenceCodeShippingConfiguration()
		throws Exception {

		super.
			testGraphQLGetProductByExternalReferenceCodeShippingConfiguration();
	}

	@Ignore
	@Override
	@Test
	public void testGraphQLGetProductByExternalReferenceCodeShippingConfigurationNotFound()
		throws Exception {

		super.
			testGraphQLGetProductByExternalReferenceCodeShippingConfigurationNotFound();
	}

	@Ignore
	@Override
	@Test
	public void testGraphQLGetProductIdShippingConfiguration()
		throws Exception {

		super.testGraphQLGetProductIdShippingConfiguration();
	}

	@Ignore
	@Override
	@Test
	public void testGraphQLGetProductIdShippingConfigurationNotFound()
		throws Exception {

		super.testGraphQLGetProductIdShippingConfigurationNotFound();
	}

	@Ignore
	@Override
	@Test
	public void testPatchProductByExternalReferenceCodeShippingConfiguration()
		throws Exception {

		super.testPatchProductByExternalReferenceCodeShippingConfiguration();
	}

	@Override
	@Test
	public void testPatchProductIdShippingConfiguration() throws Exception {
		_testPatchProductIdShippingConfigurationProductVersioning();
	}

	private void _assertCProductCPDefinitionsCount(
		long cProductId, int status, int expectedCount) {

		List<CPDefinition> cpDefinitions =
			_cpDefinitionLocalService.getCProductCPDefinitions(
				cProductId, status, QueryUtil.ALL_POS, QueryUtil.ALL_POS);

		Assert.assertEquals(
			cpDefinitions.toString(), expectedCount, cpDefinitions.size());
	}

	private void _testPatchProductIdShippingConfigurationProductVersioning()
		throws Exception {

		CommerceCatalog commerceCatalog = CPTestUtil.getSystemCommerceCatalog(
			testCompany.getCompanyId());

		CPDefinition cpDefinition1 = CPTestUtil.addCPDefinitionFromCatalog(
			commerceCatalog.getGroupId(), "simple", null, null, true, true,
			WorkflowConstants.STATUS_APPROVED);

		_cpDefinitions.add(cpDefinition1);

		cpDefinition1.setFreeShipping(false);

		cpDefinition1 = _cpDefinitionLocalService.updateCPDefinition(
			cpDefinition1);

		_assertCProductCPDefinitionsCount(
			cpDefinition1.getCProductId(), WorkflowConstants.STATUS_APPROVED,
			1);

		try (CompanyConfigurationTemporarySwapper
				companyConfigurationTemporarySwapper =
					new CompanyConfigurationTemporarySwapper(
						testCompany.getCompanyId(),
						CProductVersionConfiguration.class.getName(),
						HashMapDictionaryBuilder.<String, Object>put(
							"enabled", true
						).build())) {

			ProductShippingConfiguration productShippingConfiguration1 =
				productShippingConfigurationResource.
					getProductIdShippingConfiguration(
						cpDefinition1.getCProductId());

			productShippingConfiguration1.setFreeShipping(true);

			productShippingConfigurationResource.
				patchProductIdShippingConfiguration(
					cpDefinition1.getCProductId(),
					productShippingConfiguration1);

			_assertCProductCPDefinitionsCount(
				cpDefinition1.getCProductId(),
				WorkflowConstants.STATUS_APPROVED, 1);

			_assertCProductCPDefinitionsCount(
				cpDefinition1.getCProductId(), WorkflowConstants.STATUS_DRAFT,
				1);

			CPDefinition cpDefinition2 =
				_cpDefinitionLocalService.fetchCPDefinitionByCProductId(
					cpDefinition1.getCProductId(),
					WorkflowConstants.STATUS_DRAFT);

			_cpDefinitions.add(cpDefinition2);

			Assert.assertTrue(cpDefinition2.isFreeShipping());

			cpDefinition1 = _cpDefinitionLocalService.fetchCPDefinition(
				cpDefinition1.getCPDefinitionId());

			Assert.assertFalse(cpDefinition1.isFreeShipping());

			ProductShippingConfiguration productShippingConfiguration2 =
				productShippingConfigurationResource.
					getProductIdShippingConfiguration(
						cpDefinition1.getCProductId());

			productShippingConfiguration2.setFreeShipping(false);

			productShippingConfigurationResource.
				patchProductIdShippingConfiguration(
					cpDefinition1.getCProductId(),
					productShippingConfiguration2);

			_assertCProductCPDefinitionsCount(
				cpDefinition1.getCProductId(),
				WorkflowConstants.STATUS_APPROVED, 1);
			_assertCProductCPDefinitionsCount(
				cpDefinition1.getCProductId(), WorkflowConstants.STATUS_DRAFT,
				1);

			CPDefinition cpDefinition3 =
				_cpDefinitionLocalService.fetchCPDefinitionByCProductId(
					cpDefinition1.getCProductId(),
					WorkflowConstants.STATUS_DRAFT);

			Assert.assertEquals(
				cpDefinition2.getCPDefinitionId(),
				cpDefinition3.getCPDefinitionId());
			Assert.assertFalse(cpDefinition3.isFreeShipping());
		}
	}

	@Inject
	private CPDefinitionLocalService _cpDefinitionLocalService;

	@DeleteAfterTestRun
	private List<CPDefinition> _cpDefinitions = new ArrayList<>();

}