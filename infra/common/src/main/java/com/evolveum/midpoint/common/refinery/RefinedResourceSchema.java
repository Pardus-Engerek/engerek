/**
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.common.refinery;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import com.evolveum.midpoint.prism.Definition;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismObjectDefinition;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.processor.ObjectClassComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceAttributeContainerDefinition;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.Dumpable;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceAccountTypeDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SchemaHandlingType;

/**
 * @author semancik
 *
 */
public class RefinedResourceSchema extends PrismSchema implements Dumpable, DebugDumpable {
	
	private static final String USER_DATA_KEY_PARSED_RESOURCE_SCHEMA = RefinedResourceSchema.class.getName()+".parsedResourceSchema";
	private static final String USER_DATA_KEY_REFINED_SCHEMA = RefinedResourceSchema.class.getName()+".refinedSchema";
	
	private ResourceSchema originalResourceSchema;
	
	private RefinedResourceSchema(ResourceType resourceType, ResourceSchema originalResourceSchema, PrismContext prismContext) {
		super(resourceType.getNamespace(), prismContext);
		this.originalResourceSchema = originalResourceSchema;
	}
	
	public Collection<RefinedAccountDefinition> getAccountDefinitions() {
		Set<RefinedAccountDefinition> accounts = new HashSet<RefinedAccountDefinition>();
		for (Definition def: definitions) {
			if (def instanceof RefinedAccountDefinition) {
				RefinedAccountDefinition rad = (RefinedAccountDefinition)def;
				accounts.add(rad);
			}
		}
		return accounts;
	}
	
	public ResourceSchema getOriginalResourceSchema() {
		return originalResourceSchema;
	}

	/**
	 * if null accountType is provided, default account definition is returned.
	 */
	public RefinedAccountDefinition getAccountDefinition(String accountType) {
		for (RefinedAccountDefinition acctDef: getAccountDefinitions()) {
			if (accountType == null && acctDef.isDefault()) {
				return acctDef;
			}
			if (acctDef.getAccountTypeName().equals(accountType)) {
				return acctDef;
			}
		}
		return null;
	}
	
	public RefinedAccountDefinition getDefaultAccountDefinition() {
		return getAccountDefinition(null);
	}
	
	public PrismObjectDefinition<AccountShadowType> getObjectDefinition(String accountType) {
		return getAccountDefinition(accountType).getObjectDefinition();
	}
	
	public PrismObjectDefinition<AccountShadowType> getObjectDefinition(AccountShadowType shadow) {
		return getObjectDefinition(shadow.getAccountType());
	}
		
	private void add(RefinedAccountDefinition refinedAccountDefinition) {
		definitions.add(refinedAccountDefinition);
	}
	
	/**
	 * If already refined, return the version created before
	 */
	public static RefinedResourceSchema getRefinedSchema(ResourceType resourceType, PrismContext prismContext) throws SchemaException {
		PrismObject<ResourceType> resource = resourceType.asPrismObject();
		Object userDataEntry = resource.getUserData(USER_DATA_KEY_REFINED_SCHEMA);
		if (userDataEntry != null) {
			if (userDataEntry instanceof RefinedResourceSchema) {
				return (RefinedResourceSchema)userDataEntry;
			} else {
				throw new IllegalStateException("Expected RefinedResourceSchema under user data key "+USER_DATA_KEY_REFINED_SCHEMA+
						"in "+resource+", but got "+userDataEntry.getClass());
			}
		} else {
			RefinedResourceSchema refinedSchema = parse(resourceType, prismContext);
			resource.setUserData(USER_DATA_KEY_REFINED_SCHEMA, refinedSchema);
			return refinedSchema;
		}
	}
	
	public static boolean hasRefinedSchema(ResourceType resourceType) {
		PrismObject<ResourceType> resource = resourceType.asPrismObject();
		return resource.getUserData(USER_DATA_KEY_REFINED_SCHEMA) != null;
	}
	
	public static ResourceSchema getResourceSchema(ResourceType resourceType, PrismContext prismContext) throws SchemaException {
		Element resourceXsdSchema = ResourceTypeUtil.getResourceXsdSchema(resourceType);
		if (resourceXsdSchema == null) {
			return null;
		}
		PrismObject<ResourceType> resource = resourceType.asPrismObject();
		Object userDataEntry = resource.getUserData(USER_DATA_KEY_PARSED_RESOURCE_SCHEMA);
		if (userDataEntry != null) {
			if (userDataEntry instanceof ResourceSchema) {
				return (ResourceSchema)userDataEntry;
			} else {
				throw new IllegalStateException("Expected ResourceSchema under user data key "+
						USER_DATA_KEY_PARSED_RESOURCE_SCHEMA+ "in "+resource+", but got "+userDataEntry.getClass());
			}
		} else {
			ResourceSchema parsedSchema = ResourceSchema.parse(resourceXsdSchema, prismContext);
			if (parsedSchema == null) {
				throw new IllegalStateException("Parsed schema is null: most likely an internall error");
			}
			resource.setUserData(USER_DATA_KEY_PARSED_RESOURCE_SCHEMA, parsedSchema);
			return parsedSchema;
		}
	}
	
	public static void setParsedResourceSchemaConditional(ResourceType resourceType, ResourceSchema parsedSchema) {
		if (hasParsedSchema(resourceType)) {
			return;
		}
		PrismObject<ResourceType> resource = resourceType.asPrismObject();
		resource.setUserData(USER_DATA_KEY_PARSED_RESOURCE_SCHEMA, parsedSchema);
	}

	public static boolean hasParsedSchema(ResourceType resourceType) {
		PrismObject<ResourceType> resource = resourceType.asPrismObject();
		return resource.getUserData(USER_DATA_KEY_PARSED_RESOURCE_SCHEMA) != null;
	}

	public static RefinedResourceSchema parse(ResourceType resourceType, PrismContext prismContext) throws SchemaException {
		
		ResourceSchema originalResourceSchema = getResourceSchema(resourceType, prismContext);
		if (originalResourceSchema == null) {
			throw new IllegalArgumentException("Cannot determine resource schema from " + resourceType.asPrismObject());
		}
		
		SchemaHandlingType schemaHandling = resourceType.getSchemaHandling();
		
		RefinedResourceSchema rSchema = new RefinedResourceSchema(resourceType, originalResourceSchema, prismContext);
		
		if (schemaHandling != null) {
		
			if (schemaHandling.getAccountType() != null && !schemaHandling.getAccountType().isEmpty()) {
		
				parseAccountTypesFromSchemaHandling(rSchema, resourceType, schemaHandling, prismContext, 
						"definition of "+ObjectTypeUtil.toShortString(resourceType));
				
			} else {
				parseAccountTypesFromSchema(rSchema, resourceType, prismContext, 
						"definition of "+ObjectTypeUtil.toShortString(resourceType));
			}
			
		} else {
			parseAccountTypesFromSchema(rSchema, resourceType, prismContext, 
					"definition of "+ObjectTypeUtil.toShortString(resourceType));
		}
		
		return rSchema;
	}

	private static void parseAccountTypesFromSchemaHandling(RefinedResourceSchema rSchema, ResourceType resourceType,
			SchemaHandlingType schemaHandling, PrismContext prismContext, String contextDescription) throws SchemaException {
		
		RefinedAccountDefinition rAccountDefDefault = null;
		for (ResourceAccountTypeDefinitionType accountTypeDefType: schemaHandling.getAccountType()) {
			String accountTypeName = accountTypeDefType.getName();
			RefinedAccountDefinition rAccountDef = RefinedAccountDefinition.parse(accountTypeDefType, resourceType, rSchema, prismContext, "account type '"+accountTypeName+"', in "+contextDescription);
			
			if (rAccountDef.isDefault()) {
				if (rAccountDefDefault == null) {
					rAccountDefDefault = rAccountDef;
				} else {
					throw new SchemaException("More than one default account definitions ("+rAccountDefDefault+", "+rAccountDef+") in " + contextDescription);
				}
			}
				
			rSchema.add(rAccountDef);
		}		
	}

	private static void parseAccountTypesFromSchema(RefinedResourceSchema rSchema, ResourceType resourceType,
			PrismContext prismContext, String contextDescription) throws SchemaException {

		RefinedAccountDefinition rAccountDefDefault = null;
		for(ObjectClassComplexTypeDefinition accountDef: rSchema.getOriginalResourceSchema().getObjectClassDefinitions()) {
			QName objectClassname = accountDef.getTypeName();
			RefinedAccountDefinition rAccountDef = RefinedAccountDefinition.parse(accountDef, resourceType, rSchema, prismContext, 
					"object class "+objectClassname+" (interpreted as account type definition), in "+contextDescription);
			
			if (rAccountDef.isDefault()) {
				if (rAccountDefDefault == null) {
					rAccountDefDefault = rAccountDef;
				} else {
					throw new SchemaException("More than one default account definitions ("+rAccountDefDefault+", "+rAccountDef+") in " + contextDescription);
				}
			}
				
			rSchema.add(rAccountDef);
		}
		
	}
	
//	@Override
//	public <T extends ObjectType> PrismObject<T> parseObjectType(T objectType) throws SchemaException {
//		if (objectType instanceof AccountShadowType) {
//			AccountShadowType accountShadowType = (AccountShadowType)objectType;
//			String accountType = accountShadowType.getAccountType();
//			RefinedAccountDefinition accountDefinition = null;
//			if (accountType == null) {
//				accountDefinition = getDefaultAccountDefinition();
//				if (accountDefinition == null) {
//					throw new IllegalArgumentException("Definition for default account type was not found in "+this+", the type was specified in "+ObjectTypeUtil.toShortString(accountShadowType));
//				}
//			} else {
//				accountDefinition = getAccountDefinition(accountType);
//				if (accountDefinition == null) {
//					throw new IllegalArgumentException("Definition for account type "+accountType+" was not found in "+this+", the type was specified in "+ObjectTypeUtil.toShortString(accountShadowType));
//				}
//			}
//			return (PrismObject<T>) accountDefinition.getObjectDefinition().parseObjectType(accountShadowType);
//		} else {
//			throw new IllegalArgumentException("Refined resource schema can only parse instances of AccountShadowType");
//		}
//	}

	@Override
	public String toString() {
		return "RSchema(ns=" + namespace + ")";
	}

	/**
	 * Make sure that the specified shadow has definitions pointing to this refined schema.
	 */
	public <T extends ResourceObjectShadowType> PrismObject<T> refine(PrismObject<T> shadow) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

}
