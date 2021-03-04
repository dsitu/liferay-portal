<#assign
	cancelledCommerceOrderModels = dataFactory.newCommerceOrderModels(commerceChannelGroupModels, defaultCommerceAccountEntryModel.accountEntryId, commerceCurrencyModel.commerceCurrencyId, 0, 0, 8)

	pendingCommerceOrderModels = dataFactory.newCommerceOrderModels(commerceChannelGroupModels, defaultCommerceAccountEntryModel.accountEntryId, commerceCurrencyModel.commerceCurrencyId, 0, 0, 1)

	cpInstanceModel = cpInstanceModels[0]
/>
<#list cancelledCommerceOrderModels as cancelledCommerceOrderModel>
	${dataFactory.toInsertSQL(cancelledCommerceOrderModel)}

	${dataFactory.toInsertSQL(dataFactory.newCommerceOrderItemModel(cancelledCommerceOrderModel, commercePriceListModel, dataFactory.getCProductId(cpInstanceModel.CPDefinitionId, cpDefinitionModels), cpInstanceModel))}
</#list>

<#list pendingCommerceOrderModels as pendingCommerceOrderModel>
	${dataFactory.toInsertSQL(pendingCommerceOrderModel)}

	${dataFactory.toInsertSQL(dataFactory.newCommerceOrderItemModel(pendingCommerceOrderModel, commercePriceListModel, dataFactory.getCProductId(cpInstanceModel.CPDefinitionId, cpDefinitionModels), cpInstanceModel))}
</#list>