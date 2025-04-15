<#list dataFactory.newOrganizationModels() as organizationModel>
	<#if (dataFactory.maxOrganizationSiteCount > organizationModel?index)>
		<#assign
			organizationGroupModel = dataFactory.newOrganizationGroupModel(organizationModel, true)
			organizationName = organizationModel.name
			userModel = userModels[organizationModel?index]

			homePageContentLayoutModels = dataFactory.newContentPageLayoutModels(organizationGroupModel.groupId, "home")
			homePageSegmentsExperienceModel = dataFactory.newSegmentsExperienceModel(homePageContentLayoutModels)
		/>

		${dataFactory.toInsertSQL(homePageSegmentsExperienceModel)}

		<@insertContentPageLayout
			_fragmentEntryLinkModels = dataFactory.newFragmentEntryLinkModels(homePageContentLayoutModels, homePageSegmentsExperienceModel.getSegmentsExperienceId())
			_layoutModels = homePageContentLayoutModels
			_templateFileName = "default-homepage-layout-definition.json"
		/>

		<#list dataFactory.newGroupLayoutModels(organizationGroupModel.groupId) as groupLayoutModel>
			<@insertLayout _layoutModel = groupLayoutModel />
		</#list>

		<@insertGroup _groupModel = organizationGroupModel />

		${dataFactory.toInsertSQL("Users_Groups", userModel.companyId, organizationGroupModel.groupId, userModel.userId)}
	<#else>
		<#assign organizationGroupModel = dataFactory.newOrganizationGroupModel(organizationModel, false) />

		<@insertGroup _groupModel = organizationGroupModel />
	</#if>

	${csvFileWriter.write("organization", virtualHostModel.hostname + "," + organizationName + "\n")}

	${dataFactory.toInsertSQL(organizationModel)}
</#list>