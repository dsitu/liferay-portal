/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

import {getRandomInt} from '../utils/util';
import {ApiHelpers} from './ApiHelpers';

type TCatalog = {
	accountId?: number;
	currencyCode?: string;
	defaultLanguageId?: string;
	name?: string;
};

type TProduct = {
	active?: boolean;
	catalogId: number;
	name?: {
		[key: string]: string;
	};
	productId?: number;
	productSpecifications?: any[];
	productStatus?: number;
	productType?: string;
	skus?: TSku[];
};

type TSku = {
	cost: number;
	id?: number;
	price: number;
	published: boolean;
	purchasable: boolean;
	sku: string;
};

export class HeadlessCommerceAdminCatalogApiHelper {
	readonly apiHelpers: ApiHelpers;
	readonly basePath: string;

	constructor(apiHelpers: ApiHelpers) {
		this.apiHelpers = apiHelpers;
		this.basePath = 'headless-commerce-admin-catalog/v1.0/';
	}

	async deleteAttachment(attachmentId: string) {
		return this.apiHelpers.delete(
			`${this.apiHelpers.baseUrl}${this.basePath}/attachment/${attachmentId}`
		);
	}

	async deleteCatalog(catalogId: string) {
		return this.apiHelpers.delete(
			`${this.apiHelpers.baseUrl}${this.basePath}/catalog/${catalogId}`
		);
	}

	async deleteOption(optionId: string) {
		return this.apiHelpers.delete(
			`${this.apiHelpers.baseUrl}${this.basePath}/options/${optionId}`
		);
	}

	async deleteOptionCategory(optionCategoryId: string) {
		return this.apiHelpers.delete(
			`${this.apiHelpers.baseUrl}${this.basePath}/optionCategories/${optionCategoryId}`
		);
	}

	async deleteProduct(productId: number) {
		return this.apiHelpers.delete(
			`${this.apiHelpers.baseUrl}${this.basePath}/products/${productId}`
		);
	}

	async deleteProductOption(productOptionId: string) {
		return this.apiHelpers.delete(
			`${this.apiHelpers.baseUrl}${this.basePath}/productOptions/${productOptionId}`
		);
	}

	async deleteProductOptionValue(productOptionValueId: string) {
		return this.apiHelpers.delete(
			`${this.apiHelpers.baseUrl}${this.basePath}/productOptionValues/${productOptionValueId}`
		);
	}

	async deleteSpecification(specificationId: string) {
		return this.apiHelpers.delete(
			`${this.apiHelpers.baseUrl}${this.basePath}/specifications/${specificationId}`
		);
	}

	async getCatalog(catalogId: string) {
		return this.apiHelpers.get(
			`${this.apiHelpers.baseUrl}${this.basePath}/catalogs/${catalogId}`
		);
	}

	async getOptionCategory(optionCategoryId: string) {
		return this.apiHelpers.get(
			`${this.apiHelpers.baseUrl}${this.basePath}/optionCategories/${optionCategoryId}`
		);
	}

	async getProduct(productId: string) {
		return this.apiHelpers.get(
			`${this.apiHelpers.baseUrl}${this.basePath}/products/${productId}`
		);
	}

	async getSpecification(specificationId: string) {
		return this.apiHelpers.get(
			`${this.apiHelpers.baseUrl}${this.basePath}/specifications/${specificationId}`
		);
	}

	async postAttachment(
		productId: number,
		fileEntryId: number,
		title: string = 'Attachment' + getRandomInt()
	) {
		const postAttachment = await this.apiHelpers.post(
			`${this.apiHelpers.baseUrl}${this.basePath}/products/${productId}/attachments`,
			{
				fileEntryId,
				title: {en_US: title},
			}
		);

		return postAttachment;
	}

	async postCatalog(catalog?: TCatalog) {
		return await this.apiHelpers.post(
			`${this.apiHelpers.baseUrl}${this.basePath}/catalogs`,
			{
				accountId: 0,
				currencyCode: 'USD',
				defaultLanguageId: 'en_US',
				name: 'Catalog' + getRandomInt(),
				...(catalog || {}),
			}
		);
	}

	async postOption(
		fieldType: string = 'select',
		key: string = 'OptionKey' + getRandomInt(),
		name: string = 'OptionName' + getRandomInt()
	) {
		const postOptionCategory = await this.apiHelpers.post(
			`${this.apiHelpers.baseUrl}${this.basePath}/options`,
			{
				fieldType,
				key,
				name: {
					en_US: name,
				},
			}
		);

		return postOptionCategory;
	}

	async postOptionCategory(
		optionCategoryName: string = 'OptionCategory' + getRandomInt(),
		priority: number = getRandomInt()
	) {
		const postOptionCategory = await this.apiHelpers.post(
			`${this.apiHelpers.baseUrl}${this.basePath}/optionCategories`,
			{
				key: optionCategoryName,
				priority,
				title: {
					en_US: optionCategoryName,
				},
			}
		);

		return postOptionCategory;
	}

	async postProduct(product: TProduct): Promise<TProduct> {
		return await this.apiHelpers.post(
			`${this.apiHelpers.baseUrl}${this.basePath}/products?nestedFields=skus`,
			{
				active: true,
				catalogId: 0,
				name: {
					en_US: 'Product' + getRandomInt(),
				},
				productStatus: 0,
				productType: 'simple',
				skus: [
					{
						cost: 0,
						price: 0,
						published: true,
						purchasable: true,
						sku: 'Sku' + getRandomInt(),
					},
				],
				...product,
			}
		);
	}

	async postProductOption(
		productId: number,
		dateTime: boolean,
		fieldType: string,
		key: string,
		name: string,
		optionId: number,
		priority: number,
		required: boolean,
		skuContributor: boolean
	) {
		const postProductOption = await this.apiHelpers.post(
			`${this.apiHelpers.baseUrl}${this.basePath}/products/${productId}/productOptions`,
			[
				{
					dateTime,
					fieldType,
					key,
					name: {
						en_US: name,
					},
					optionId,
					priority,
					required,
					skuContributor,
				},
			]
		);

		return postProductOption;
	}

	async postProductOptionValues(
		productOptionId: number,
		duration: number,
		durationType: string,
		key: string,
		name: string,
		optionValueDate: string,
		preselected: boolean,
		priority: number
	) {
		const postProductOption = await this.apiHelpers.post(
			`${this.apiHelpers.baseUrl}${this.basePath}/productOptions/${productOptionId}/productOptionValues`,
			{
				duration,
				durationType,
				key,
				name: {
					en_US: name,
				},
				optionValueDate,
				preselected,
				priority,
			}
		);

		return postProductOption;
	}

	async postSpecification(
		facetable: boolean = true,
		specificationTitle: string = 'Specification' + getRandomInt(),
		optionCategory?: DataObject
	) {
		if (typeof optionCategory !== 'undefined') {
			return this.apiHelpers.post(
				`${this.apiHelpers.baseUrl}${this.basePath}/specifications`,
				{
					facetable,
					key: specificationTitle,
					optionCategory,
					title: {
						en_US: specificationTitle,
					},
				}
			);
		}

		return this.apiHelpers.post(
			`${this.apiHelpers.baseUrl}${this.basePath}/specifications`,
			{
				facetable,
				key: specificationTitle,
				title: {
					en_US: specificationTitle,
				},
			}
		);
	}
}
