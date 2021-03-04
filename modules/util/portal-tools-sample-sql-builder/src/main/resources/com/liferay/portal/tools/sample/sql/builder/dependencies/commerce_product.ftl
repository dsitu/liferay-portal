<#assign
	commerceAccountEntryModels = dataFactory.newCommerceAccountEntryModels()

	defaultCommerceAccountEntryModel = commerceAccountEntryModels[0]

	commerceAccountEntryGroupModels = dataFactory.newCommerceAccountEntryGroupModels(commerceAccountEntryModels)

	commerceCurrencyModel = dataFactory.newCommerceCurrencyModel()

	commerceCatalogModel = dataFactory.newCommerceCatalogModel(commerceCurrencyModel)

	commerceCatalogGroupModel = dataFactory.newCommerceCatalogGroupModel(commerceCatalogModel)

	commercePriceListModel = dataFactory.newCommercePriceListModel(commerceCatalogGroupModel.groupId, commerceCurrencyModel.commerceCurrencyId, true, true, "price-list")

	commerceGroupModels = dataFactory.newCommerceGroupModels()

	commerceChannelModels = dataFactory.newCommerceChannelModels(commerceGroupModels, commerceCurrencyModel)

	commerceChannelGroupModels = dataFactory.newCommerceChannelGroupModels(commerceChannelModels)

	cpTaxCategoryModel = dataFactory.newCPTaxCategoryModel()

	cpOptionCategoryModels = dataFactory.newCPOptionCategoryModels()

	cpSpecificationOptionModels = dataFactory.newCPSpecificationOptionModels(cpOptionCategoryModels)

	commerceInventoryWarehouseModels = dataFactory.newCommerceInventoryWarehouseModels()

	cProductModels = dataFactory.newCProductModels(commerceCatalogGroupModel.groupId)

	cpDefinitionModels = dataFactory.newCPDefinitionModels(cpTaxCategoryModel, cProductModels)

	cpInstanceModels = dataFactory.newCPInstanceModels(cpDefinitionModels)
/>

<#list commerceAccountEntryModels as commerceAccountEntryModel>
	${dataFactory.toInsertSQL(commerceAccountEntryModel)}

	${dataFactory.toInsertSQL(dataFactory.newAccountEntryUserRelModel(sampleUserModel, commerceAccountEntryModel.accountEntryId))}
</#list>

${dataFactory.toInsertSQL(commerceCurrencyModel)}

${dataFactory.toInsertSQL(commerceCatalogModel)}

${dataFactory.toInsertSQL(commercePriceListModel)}

${dataFactory.toInsertSQL(dataFactory.newCommerceCatalogResourcePermissionModel(commerceCatalogModel))}

<#list commerceChannelModels as commerceChannelModel>
	${dataFactory.toInsertSQL(commerceChannelModel)}
</#list>

${dataFactory.toInsertSQL(cpTaxCategoryModel)}

<#list cpOptionCategoryModels as cpOptionCategoryModel>
	${dataFactory.toInsertSQL(cpOptionCategoryModel)}
</#list>

<#list cpSpecificationOptionModels as cpSpecificationOptionModel>
	${dataFactory.toInsertSQL(cpSpecificationOptionModel)}
</#list>

<#list commerceInventoryWarehouseModels as commerceInventoryWarehouseModel>
	<#list commerceChannelModels as commerceChannelModel>
		${dataFactory.toInsertSQL(dataFactory.newCommerceChannelRelModel(dataFactory.commerceInventoryWarehouseClassNameId, commerceInventoryWarehouseModel.commerceInventoryWarehouseId, commerceChannelModel.commerceChannelId))}
	</#list>

	${dataFactory.toInsertSQL(commerceInventoryWarehouseModel)}
</#list>

<#list cProductModels as cProductModel>
	<#assign
		cProductModel = dataFactory.setCProductModelPublishedCPDefinitionId(cProductModel, cpDefinitionModels)

		friendlyURLEntryModel = dataFactory.newFriendlyURLEntryModel(globalGroupModel.groupId, dataFactory.CProductClassNameId, cProductModel.CProductId)

		friendlyURLEntryLocalizationModel = dataFactory.newFriendlyURLEntryLocalizationModel(friendlyURLEntryModel, "definition-" + cProductModel.publishedCPDefinitionId)
	/>

	${dataFactory.toInsertSQL(cProductModel)}

	${dataFactory.toInsertSQL(friendlyURLEntryModel)}

	${dataFactory.toInsertSQL(friendlyURLEntryLocalizationModel)}

	${dataFactory.toInsertSQL(dataFactory.newFriendlyURLEntryMapping(friendlyURLEntryModel))}

	${csvFileWriter.write("cpFriendlyURLEntry", friendlyURLEntryLocalizationModel.urlTitle + "\n")}

</#list>

<#list cpDefinitionModels as cpDefinitionModel>
	<#list commerceChannelModels as commerceChannelModel>
		${dataFactory.toInsertSQL(dataFactory.newCommerceChannelRelModel(dataFactory.CPDefinitionClassNameId, cpDefinitionModel.CPDefinitionId, commerceChannelModel.commerceChannelId))}
	</#list>

	${dataFactory.toInsertSQL(dataFactory.newCPDefinitionModelAssetEntryModel(cpDefinitionModel, commerceCatalogGroupModel.groupId))}

	${dataFactory.toInsertSQL(dataFactory.newCPDefinitionLocalizationModel(cpDefinitionModel))}

	<#list dataFactory.getSequence(dataFactory.maxCPDefinitionSpecificationOptionValueCount) as cpDefinitionSpecificationOptionValueCount>
		<#assign
			cpSpecificationOptionModel = cpSpecificationOptionModels[cpDefinitionSpecificationOptionValueCount - 1]

			cpDefinitionSpecificationOptionValueModel = dataFactory.newCPDefinitionSpecificationOptionValueModel(cpDefinitionModel.CPDefinitionId, cpSpecificationOptionModel.CPSpecificationOptionId, cpSpecificationOptionModel.CPOptionCategoryId, cpDefinitionSpecificationOptionValueCount)
		/>

		${dataFactory.toInsertSQL(cpDefinitionSpecificationOptionValueModel)}
	</#list>

	${dataFactory.toInsertSQL(cpDefinitionModel)}
</#list>

<#list cpInstanceModels as cpInstanceModel>
	${dataFactory.toInsertSQL(cpInstanceModel)}

	${dataFactory.toInsertSQL(dataFactory.newCommercePriceEntryModel(commercePriceListModel.commercePriceListId, cpInstanceModel.uuid, dataFactory.getCProductId(cpInstanceModel.CPDefinitionId, cpDefinitionModels)))}

	<#list commerceInventoryWarehouseModels as commerceInventoryWarehouseModel>
		${dataFactory.toInsertSQL(dataFactory.newCommerceInventoryWarehouseItemModel(commerceInventoryWarehouseModel, cpInstanceModel))}
	</#list>
</#list>

<#assign
	cpOptionModel = dataFactory.newCPOptionModel("select", 1)
/>

${dataFactory.toInsertSQL(cpOptionModel)}

${dataFactory.toInsertSQL(dataFactory.newCPOptionValueModel(cpOptionModel.CPOptionId, 1))}

<#include "commerce_product_attachment_file_entries.ftl">

<#include "commerce_orders.ftl">

<#list commerceGroupModels as commerceGroupModel>
	${dataFactory.toInsertSQL(commerceGroupModel)}

	<#assign
		commerceSiteNavigationPortletPreferencesModels = dataFactory.newCommerceSiteNavigationPortletPreferencesModels(commerceGroupModel)
	/>

	<#list commerceSiteNavigationPortletPreferencesModels as commerceSiteNavigationPortletPreferencesModel>
		${dataFactory.toInsertSQL(commerceSiteNavigationPortletPreferencesModel)}
	</#list>

	<#list dataFactory.newCommerceSiteNavigationPortletDDMTemplateModels(commerceGroupModel.groupId) as commerceSiteNavigationPortletDDMTemplateModel>
		${dataFactory.toInsertSQL(commerceSiteNavigationPortletDDMTemplateModel)}
	</#list>

	<#list dataFactory.newCommerceSiteNavigationPortletPreferenceValueModels(commerceSiteNavigationPortletPreferencesModels) as commerceSiteNavigationPortletPreferenceValueModel>
		${dataFactory.toInsertSQL(commerceSiteNavigationPortletPreferenceValueModel)}
	</#list>

	<#assign
		commerceLayoutSetModels = dataFactory.newLayoutSetModels(commerceGroupModel.groupId, "minium_WAR_miniumtheme")
	/>

	<#list commerceLayoutSetModels as commerceLayoutSetModel>
		${dataFactory.toInsertSQL(commerceLayoutSetModel)}
	</#list>

	<#assign
		commerceLayoutModels = dataFactory.newCommerceLayoutModels(commerceGroupModel.groupId)
	/>

	<#list commerceLayoutModels as commerceLayoutModel>
		<#assign
			portletPreferencesModels = dataFactory.newCommercePortletPreferencesModels(commerceLayoutModel)

			portletPreferenceValueModels = dataFactory.newCommerceLayoutPortletPreferenceValueModels(portletPreferencesModels)
		/>

		<#list portletPreferencesModels as portletPreferencesModel>
			${dataFactory.toInsertSQL(portletPreferencesModel)}
		</#list>

		<#list portletPreferenceValueModels as portletPreferenceValueModel>
			${dataFactory.toInsertSQL(portletPreferenceValueModel)}
		</#list>

		<@insertLayout _layoutModel=commerceLayoutModel />
	</#list>
</#list>

<@insertGroup _groupModel=commerceCatalogGroupModel />

<#list commerceAccountEntryGroupModels as commerceAccountEntryGroupModel>
	<@insertGroup _groupModel=commerceAccountEntryGroupModel />
</#list>

<#list commerceChannelGroupModels as commerceChannelGroupModel>
	<@insertGroup _groupModel=commerceChannelGroupModel />

	<#assign
		commerceB2BSiteTypePortletPreferencesModel = dataFactory.newCommerceB2BSiteTypePortletPreferencesModel(commerceChannelGroupModel.groupId)
	/>

	${dataFactory.toInsertSQL(commerceB2BSiteTypePortletPreferencesModel)}

	${dataFactory.toInsertSQL(dataFactory.newCommerceB2BSiteTypePortletPreferenceValueModel(commerceB2BSiteTypePortletPreferencesModel))}
</#list>