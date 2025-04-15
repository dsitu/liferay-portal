<#list dataFactory.newOrganizationModels() as organizationModel>
	<#if (dataFactory.maxOrganizationSiteCount > organizationModel?index)>
		<#assign
			organizationGroupModel = dataFactory.newOrganizationGroupModel(organizationModel, true)
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
		<@insertGroup _groupModel = dataFactory.newOrganizationGroupModel(organizationModel, false) />
	</#if>

	${csvFileWriter.write("organization", virtualHostModel.hostname + "," + organizationModel.name + "\n")}

	${dataFactory.toInsertSQL(organizationModel)}
</#list>