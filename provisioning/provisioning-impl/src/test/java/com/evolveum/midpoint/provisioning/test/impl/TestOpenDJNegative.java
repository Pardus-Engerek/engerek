/*
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
package com.evolveum.midpoint.provisioning.test.impl;

import static com.evolveum.midpoint.test.IntegrationTestTools.assertSuccess;
import static com.evolveum.midpoint.test.IntegrationTestTools.assertFailure;
import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static com.evolveum.midpoint.test.IntegrationTestTools.displayTestTile;
import static com.evolveum.midpoint.test.IntegrationTestTools.getAttributeValue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import javax.xml.namespace.QName;

import org.opends.server.types.SearchResultEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

import com.evolveum.midpoint.common.refinery.RefinedResourceSchema;
import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.Objectable;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.util.PrismAsserts;
import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.api.ResultHandler;
import com.evolveum.midpoint.provisioning.impl.ConnectorTypeManager;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorFactory;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.impl.ConnectorFactoryIcfImpl;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.QueryConvertor;
import com.evolveum.midpoint.schema.processor.ObjectClassComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceSchema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.schema.util.ResourceObjectShadowUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.AbstractIntegrationTest;
import com.evolveum.midpoint.test.IntegrationTestTools;
import com.evolveum.midpoint.test.ldap.OpenDJController;
import com.evolveum.midpoint.util.JAXBUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.CachingMetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.CapabilitiesType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.CapabilityCollectionType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.FailedOperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.OperationResultStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.OperationResultType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.XmlSchemaType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_2.ActivationCapabilityType;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_2.CredentialsCapabilityType;
import com.evolveum.prism.xml.ns._public.query_2.PagingType;
import com.evolveum.prism.xml.ns._public.query_2.QueryType;

/**
 * Test for provisioning service implementation. Using OpenDJ. But NOT STARTING IT.
 * Checking if appropriate errors are provided.
 */

@ContextConfiguration(locations = { "classpath:application-context-provisioning.xml",
		"classpath:application-context-provisioning-test.xml",
		"classpath:application-context-task.xml",
        "classpath:application-context-audit.xml",
		"classpath:application-context-repository.xml",
		"classpath:application-context-repo-cache.xml",
		"classpath:application-context-configuration-test.xml" })
@DirtiesContext
public class TestOpenDJNegative extends AbstractOpenDJTest {
	
	private static Trace LOGGER = TraceManager.getTrace(TestOpenDJNegative.class);

	@Override
	public void initSystem(Task initTask, OperationResult initResult) throws Exception {
		super.initSystem(initTask, initResult);
		
		addObjectFromFile(ACCOUNT1_REPO_FILENAME, AccountShadowType.class, initResult);
		addObjectFromFile(ACCOUNT_DELETE_REPO_FILENAME, AccountShadowType.class, initResult);
		addObjectFromFile(ACCOUNT_MODIFY_REPO_FILENAME, AccountShadowType.class, initResult);
	}
	
// We are NOT starting OpenDJ here. We want to see the blood .. err ... errors
	
	@Test
	public void test003Connection() throws Exception {
		displayTestTile("test003Connection");

		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()+".test003Connection");
		ResourceType resourceTypeBefore = repositoryService.getObject(ResourceType.class, RESOURCE_OPENDJ_OID, result).asObjectable();
		display("Resource before testResource (repository)", resourceTypeBefore);
		assertNotNull("No connector ref",resourceTypeBefore.getConnectorRef());
		assertNotNull("No connector ref OID",resourceTypeBefore.getConnectorRef().getOid());
		connector = repositoryService.getObject(ConnectorType.class, resourceTypeBefore.getConnectorRef().getOid(), result);
		ConnectorType connectorType = connector.asObjectable();
		assertNotNull(connectorType);
		XmlSchemaType xmlSchemaTypeBefore = resourceTypeBefore.getSchema();
		AssertJUnit.assertNull("Found schema before test connection. Bad test setup?", xmlSchemaTypeBefore);
		Element resourceXsdSchemaElementBefore = ResourceTypeUtil.getResourceXsdSchema(resourceTypeBefore);
		AssertJUnit.assertNull("Found schema element before test connection. Bad test setup?", resourceXsdSchemaElementBefore);
		
		// WHEN
		OperationResult	operationResult = provisioningService.testResource(RESOURCE_OPENDJ_OID);
		
		display("Test connection result (expected failure)",operationResult);
		assertFailure(operationResult);
		
		PrismObject<ResourceType> resourceRepoAfter = repositoryService.getObject(ResourceType.class,RESOURCE_OPENDJ_OID, result);
		display("Resource after testResource (repository)", resourceRepoAfter);
		ResourceType resourceTypeRepoAfter = resourceRepoAfter.asObjectable();
		display("Resource after testResource (repository, XML)", PrismTestUtil.serializeObjectToString(resourceTypeRepoAfter.asPrismObject()));
		
		XmlSchemaType xmlSchemaTypeAfter = resourceTypeRepoAfter.getSchema();
		assertNull("The schema was generated after test connection but it should not be",xmlSchemaTypeAfter);
		Element resourceXsdSchemaElementAfter = ResourceTypeUtil.getResourceXsdSchema(resourceTypeRepoAfter);
		assertNull("Schema after test connection (and should not be)", resourceXsdSchemaElementAfter);		
	}
	
	@Test
	public void test004ResourceAndConnectorCaching() throws Exception {
		displayTestTile("test004ResourceAndConnectorCaching");

		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()+".test004ResourceAndConnectorCaching");

		// WHEN
		// This should NOT throw an exception. It should just indicate the failure in results
		resource = provisioningService.getObject(ResourceType.class,RESOURCE_OPENDJ_OID, null, result);
		ResourceType resourceType = resource.asObjectable();

		// THEN
		result.computeStatus();
		display("getObject(resource) result", result);
		assertFailure(result);
		assertFailure(resource.asObjectable().getFetchResult());

		
		ConnectorInstance configuredConnectorInstance = connectorTypeManager.getConfiguredConnectorInstance(
				resource.asObjectable(), false, result);
		assertNotNull("No configuredConnectorInstance", configuredConnectorInstance);
		ResourceSchema resourceSchema = RefinedResourceSchema.getResourceSchema(resource, prismContext);
		assertNull("Resource schema found", resourceSchema);
		
		// WHEN
		PrismObject<ResourceType> resourceAgain = provisioningService.getObject(ResourceType.class,RESOURCE_OPENDJ_OID, null, result);
		
		// THEN
		result.computeStatus();
		display("getObject(resourceAgain) result", result);
		assertFailure(result);
		assertFailure(resourceAgain.asObjectable().getFetchResult());
		
		ResourceType resourceTypeAgain = resourceAgain.asObjectable();
		assertNotNull("No connector ref",resourceTypeAgain.getConnectorRef());
		assertNotNull("No connector ref OID",resourceTypeAgain.getConnectorRef().getOid());
		
		PrismContainer<Containerable> configurationContainer = resource.findContainer(ResourceType.F_CONNECTOR_CONFIGURATION);
		PrismContainer<Containerable> configurationContainerAgain = resourceAgain.findContainer(ResourceType.F_CONNECTOR_CONFIGURATION);		
		assertTrue("Configurations not equivalent", configurationContainer.equivalent(configurationContainerAgain));
		assertTrue("Configurations not equals", configurationContainer.equals(configurationContainerAgain));

		ResourceSchema resourceSchemaAgain = RefinedResourceSchema.getResourceSchema(resourceAgain, prismContext);
		assertNull("Resource schema (again)", resourceSchemaAgain);
		
		// Now we stick our nose deep inside the provisioning impl. But we need to make sure that the
		// configured connector is properly cached
		ConnectorInstance configuredConnectorInstanceAgain = connectorTypeManager.getConfiguredConnectorInstance(
				resourceTypeAgain, false, result);
		assertTrue("Connector instance was not cached", configuredConnectorInstance == configuredConnectorInstanceAgain);
	}
	
	/**
	 * This goes to local repo, therefore the expected result is ObjectNotFound.
	 * We know that the shadow does not exist. 
	 */
	@Test
	public void test110GetObjectNoShadow() throws Exception {
		final String TEST_NAME = "test110GetObjectNoShadow";
		displayTestTile(TEST_NAME);
		
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

		try {
			AccountShadowType acct = provisioningService.getObject(AccountShadowType.class, NON_EXISTENT_OID, null, result).asObjectable();
			
			AssertJUnit.fail("getObject succeeded unexpectedly");
		} catch (ObjectNotFoundException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
	}

	/**
	 * This is using the shadow to go to the resource. But it cannot as OpenDJ is down.
	 * It even cannot fetch schema. If there is no schema it does not even know how to process
	 * identifiers in the shadow. Therefore the expected result is ConfigurationException.
	 * It must not be ObjectNotFound as we do NOT know that the shadow does not exist. 
	 */
	@Test
	public void test111GetObjectShadow() throws Exception {
		final String TEST_NAME = "test111GetObjectShadow";
		displayTestTile(TEST_NAME);
		
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);
				
		try {

			AccountShadowType acct = provisioningService.getObject(AccountShadowType.class, ACCOUNT1_OID, null, result).asObjectable();

			AssertJUnit.fail("getObject succeeded unexpectedly");
		} catch (ConfigurationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
	}
	
	@Test
	public void test120ListResourceObjects() throws Exception {
		final String TEST_NAME = "test120ListResourceObjects";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);
		
		try {
			// WHEN
			List<PrismObject<? extends ResourceObjectShadowType>> objectList = provisioningService.listResourceObjects(
					RESOURCE_OPENDJ_OID, RESOURCE_OPENDJ_ACCOUNT_OBJECTCLASS, null, result);
			
			AssertJUnit.fail("listResourceObjects succeeded unexpectedly");
		} catch (ConfigurationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
	}
	
	@Test
	public void test121SearchAccounts() throws SchemaException, ObjectNotFoundException,
          CommunicationException, ConfigurationException, SecurityViolationException, Exception {
		final String TEST_NAME = "test121SearchAccounts";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

      final String resourceNamespace = ResourceTypeUtil.getResourceNamespace(resource);
      QName objectClass = new QName(resourceNamespace, "AccountObjectClass");

      ObjectQuery query = ObjectQueryUtil.createResourceAndAccountQuery(resource.getOid(), objectClass, prismContext);
      
      try {
    	  
	      // WHEN
	      provisioningService.searchObjects(AccountShadowType.class, query, result);
	      
	      AssertJUnit.fail("searchObjectsIterative succeeded unexpectedly");
		} catch (ConfigurationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		display(result);
		assertFailure(result);
  	}
	
	@Test
	public void test122SearchAccountsIterative() throws SchemaException, ObjectNotFoundException,
          CommunicationException, ConfigurationException, SecurityViolationException, Exception {
		final String TEST_NAME = "test122SearchAccountsIterative";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

      final String resourceNamespace = ResourceTypeUtil.getResourceNamespace(resource);
      QName objectClass = new QName(resourceNamespace, "AccountObjectClass");

      ObjectQuery query = ObjectQueryUtil.createResourceAndAccountQuery(resource.getOid(), objectClass, prismContext);
      
      ResultHandler handler = new ResultHandler<ObjectType>() {
          @Override
          public boolean handle(PrismObject<ObjectType> prismObject, OperationResult parentResult) {
        	  AssertJUnit.fail("handler called unexpectedly");
        	  return false;
          }
      };

      try {
    	  
	      // WHEN
	      provisioningService.searchObjectsIterative(AccountShadowType.class, query, handler, result);
	      
	      AssertJUnit.fail("searchObjectsIterative succeeded unexpectedly");
		} catch (ConfigurationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
  	}
	
	@Test
	public void test130AddObject() throws Exception {
		final String TEST_NAME = "test130AddObject";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

		AccountShadowType object = parseObjectTypeFromFile(ACCOUNT_NEW_FILENAME, AccountShadowType.class);

		display("Account to add", object);

		try {
			// WHEN
			String addedObjectOid = provisioningService.addObject(object.asPrismObject(), null, result);
			
			AssertJUnit.fail("addObject succeeded unexpectedly");
		} catch (ConfigurationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
	}

	
	@Test
	public void test140DeleteObject() throws Exception {
		final String TEST_NAME = "test140DeleteObject";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

		try {

			provisioningService.deleteObject(AccountShadowType.class, ACCOUNT_DELETE_OID, null, null, result);

			AssertJUnit.fail("addObject succeeded unexpectedly");
		} catch (ConfigurationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);

	}
	
	@Test
	public void test150ModifyObject() throws Exception {
		final String TEST_NAME = "test150ModifyObject";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

		ObjectModificationType objectChange = PrismTestUtil.unmarshalObject(
				new File("src/test/resources/impl/account-change-description.xml"), ObjectModificationType.class);
		ObjectDelta<AccountShadowType> delta = DeltaConvertor.createObjectDelta(objectChange, AccountShadowType.class, PrismTestUtil.getPrismContext());
		display("Object change",delta);
		
		try {

			provisioningService.modifyObject(AccountShadowType.class, objectChange.getOid(),
					delta.getModifications(), null, result);
			
			AssertJUnit.fail("addObject succeeded unexpectedly");
		} catch (ConfigurationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
	}
	
	@Test
	public void test190Synchronize() throws Exception {
		final String TEST_NAME = "test190Synhronize";
		displayTestTile(TEST_NAME);
		// GIVEN
		Task task = taskManager.createTaskInstance(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);
		OperationResult result = task.getResult();
		
		try {

			provisioningService.synchronize(RESOURCE_OPENDJ_OID, task, result);
			
			AssertJUnit.fail("addObject succeeded unexpectedly");
		} catch (ConfigurationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
	}

	
	// =========================================================================================================
	// Now lets replace the resource with one that has schema and capabilities. And re-run some of the tests.
	// OpenDJ is still down so the results should be the same. But the code may take a different path if
	// schema is present.
	// =========================================================================================================
	
	@Test
	public void test500ReplaceResource() throws Exception {
		final String TEST_NAME = "test500ReplaceResource";
		displayTestTile(TEST_NAME);
		
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

		// Delete should work fine even though OpenDJ is down
		provisioningService.deleteObject(ResourceType.class, RESOURCE_OPENDJ_OID, null, null, result);
		
		result.computeStatus();
		assertSuccess(result);
		
		resource = addResourceFromFile(RESOURCE_OPENDJ_INITIALIZED_FILENAME, LDAP_CONNECTOR_TYPE, result);

		result.computeStatus();
		assertSuccess(result);

	}
	
	/**
	 * This goes to local repo, therefore the expected result is ObjectNotFound.
	 * We know that the shadow does not exist. 
	 */
	@Test
	public void test510GetObjectNoShadow() throws Exception {
		final String TEST_NAME = "test510GetObjectNoShadow";
		displayTestTile(TEST_NAME);
		
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

		try {
			AccountShadowType acct = provisioningService.getObject(AccountShadowType.class, NON_EXISTENT_OID, null, result).asObjectable();
			
			AssertJUnit.fail("getObject succeeded unexpectedly");
		} catch (ObjectNotFoundException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
	}

	/**
	 * This is using the shadow to go to the resource. But it cannot as OpenDJ is down. 
	 * Therefore the expected result is CommunicationException. It must not be ObjectNotFound as 
	 * we do NOT know that the shadow does not exist.
	 * Provisioning should return a repo shadow and indicate the result both in operation result and
	 * in fetchResult in the returned shadow.
	 */
	@Test
	public void test511GetObjectShadow() throws Exception {
		final String TEST_NAME = "test511GetObjectShadow";
		displayTestTile(TEST_NAME);
		
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);
				
		PrismObject<AccountShadowType> acct = provisioningService.getObject(AccountShadowType.class, ACCOUNT1_OID, null, result);

		display("Account", acct);
		
		result.computeStatus();
		display("getObject result", result);
		assertEquals("Expected result partial error but was "+result.getStatus(), 
				OperationResultStatus.PARTIAL_ERROR, result.getStatus());
		
		OperationResultType fetchResult = acct.asObjectable().getFetchResult();
		display("getObject fetchResult", fetchResult);
		assertEquals("Expected fetchResult partial error but was "+result.getStatus(), 
				OperationResultStatusType.PARTIAL_ERROR, fetchResult.getStatus());
	}

	/**
	 * This is using the shadow to go to the resource. But it cannot as OpenDJ is down. 
	 * Therefore the expected result is CommunicationException. It must not be ObjectNotFound as 
	 * we do NOT know that the shadow does not exist. 
	 */
	@Test
	public void test520ListResourceObjects() throws Exception {
		final String TEST_NAME = "test520ListResourceObjects";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);
		
		try {
			// WHEN
			List<PrismObject<? extends ResourceObjectShadowType>> objectList = provisioningService.listResourceObjects(
					RESOURCE_OPENDJ_OID, RESOURCE_OPENDJ_ACCOUNT_OBJECTCLASS, null, result);
			
			AssertJUnit.fail("listResourceObjects succeeded unexpectedly");
		} catch (CommunicationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
	}
	
	@Test
	public void test521SearchAccounts() throws SchemaException, ObjectNotFoundException,
          CommunicationException, ConfigurationException, SecurityViolationException, Exception {
		final String TEST_NAME = "test521SearchAccounts";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

      final String resourceNamespace = ResourceTypeUtil.getResourceNamespace(resource);
      QName objectClass = new QName(resourceNamespace, "AccountObjectClass");

      ObjectQuery query = ObjectQueryUtil.createResourceAndAccountQuery(resource.getOid(), objectClass, prismContext);
      
      try {
    	  
	      // WHEN
	      provisioningService.searchObjects(AccountShadowType.class, query, result);
	      
	      AssertJUnit.fail("searchObjectsIterative succeeded unexpectedly");
		} catch (CommunicationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
  	}
	
	@Test
	public void test522SearchAccountsIterative() throws SchemaException, ObjectNotFoundException,
          CommunicationException, ConfigurationException, SecurityViolationException, Exception {
		final String TEST_NAME = "test522SearchAccountsIterative";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

      final String resourceNamespace = ResourceTypeUtil.getResourceNamespace(resource);
      QName objectClass = new QName(resourceNamespace, "AccountObjectClass");

      ObjectQuery query = ObjectQueryUtil.createResourceAndAccountQuery(resource.getOid(), objectClass, prismContext);
      
      ResultHandler handler = new ResultHandler<ObjectType>() {
          @Override
          public boolean handle(PrismObject<ObjectType> prismObject, OperationResult parentResult) {
        	  AssertJUnit.fail("handler called unexpectedly");
        	  return false;
          }
      };

      try {
    	  
	      // WHEN
	      provisioningService.searchObjectsIterative(AccountShadowType.class, query, handler, result);
	      
	      AssertJUnit.fail("searchObjectsIterative succeeded unexpectedly");
		} catch (CommunicationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		display(result);
		assertFailure(result);
  	}
	
	@Test
	public void test530AddObject() throws Exception {
		final String TEST_NAME = "test530AddObject";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

		AccountShadowType object = parseObjectTypeFromFile(ACCOUNT_NEW_FILENAME, AccountShadowType.class);

		display("Account to add", object);

		// WHEN
		String addedObjectOid = provisioningService.addObject(object.asPrismObject(), null, result);
		
		// THEN
		result.computeStatus();
		display("addObject result", result);
		assertEquals("Wrong result", OperationResultStatus.HANDLED_ERROR, result.getStatus());
		
		assertEquals(ACCOUNT_NEW_OID, addedObjectOid);

		AccountShadowType repoAccountType =  repositoryService.getObject(AccountShadowType.class, ACCOUNT_NEW_OID,
				result).asObjectable();
		display("repo shadow", repoAccountType);
		PrismAsserts.assertEqualsPolyString("Name not equal.", "will", repoAccountType.getName());
		assertEquals("Wrong failedOperationType in repo", FailedOperationTypeType.ADD, repoAccountType.getFailedOperationType());
		OperationResultType repoResult = repoAccountType.getResult();
		assertNotNull("No result in shadow (repo)", repoResult);
		assertFailure("Result in shadow (repo)", repoResult);

		AccountShadowType provisioningAccountType = provisioningService.getObject(AccountShadowType.class, ACCOUNT_NEW_OID,
				null, result).asObjectable();
		display("provisioning shadow", provisioningAccountType);
		PrismAsserts.assertEqualsPolyString("Name not equal.", "will", provisioningAccountType.getName());
		assertEquals("Wrong failedOperationType in repo", FailedOperationTypeType.ADD, provisioningAccountType.getFailedOperationType());
		OperationResultType provisioningResult = provisioningAccountType.getResult();
		assertNotNull("No result in shadow (repo)", provisioningResult);
		assertFailure("Result in shadow (repo)", provisioningResult);

	}
	
	@Test
	public void test540DeleteObject() throws Exception {
		final String TEST_NAME = "test540DeleteObject";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

		// WHEN
		provisioningService.deleteObject(AccountShadowType.class, ACCOUNT_DELETE_OID, null, null, result);

		// THEN
		result.computeStatus();
		display("deleteObject result", result);
		assertEquals("Wrong result", OperationResultStatus.HANDLED_ERROR, result.getStatus());
		
		AccountShadowType repoAccountType =  repositoryService.getObject(AccountShadowType.class, ACCOUNT_DELETE_OID,
				result).asObjectable();
		display("repo shadow", repoAccountType);
		assertEquals("Wrong failedOperationType in repo", FailedOperationTypeType.DELETE, repoAccountType.getFailedOperationType());
		OperationResultType repoResult = repoAccountType.getResult();
		assertNotNull("No result in shadow (repo)", repoResult);
		assertFailure("Result in shadow (repo)", repoResult);

		AccountShadowType provisioningAccountType = provisioningService.getObject(AccountShadowType.class, ACCOUNT_DELETE_OID,
				null, result).asObjectable();
		display("provisioning shadow", provisioningAccountType);
		assertEquals("Wrong failedOperationType in repo", FailedOperationTypeType.DELETE, provisioningAccountType.getFailedOperationType());
		OperationResultType provisioningResult = provisioningAccountType.getResult();
		assertNotNull("No result in shadow (repo)", provisioningResult);
		assertFailure("Result in shadow (repo)", provisioningResult);		
	}

	@Test
	public void test550ModifyObject() throws Exception {
		final String TEST_NAME = "test150ModifyObject";
		displayTestTile(TEST_NAME);
		// GIVEN
		OperationResult result = new OperationResult(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);

		ObjectModificationType objectChange = PrismTestUtil.unmarshalObject(
				new File("src/test/resources/impl/account-change-description.xml"), ObjectModificationType.class);
		ObjectDelta<AccountShadowType> delta = DeltaConvertor.createObjectDelta(objectChange, AccountShadowType.class, PrismTestUtil.getPrismContext());
		display("Object change",delta);
		
		provisioningService.modifyObject(AccountShadowType.class, objectChange.getOid(),
				delta.getModifications(), null, result);
		
	
		// THEN
		result.computeStatus();
		display("deleteObject result", result);
		assertEquals("Wrong result", OperationResultStatus.HANDLED_ERROR, result.getStatus());
		
		AccountShadowType repoAccountType =  repositoryService.getObject(AccountShadowType.class, ACCOUNT_MODIFY_OID,
				result).asObjectable();
		display("repo shadow", repoAccountType);
		assertEquals("Wrong failedOperationType in repo", FailedOperationTypeType.MODIFY, repoAccountType.getFailedOperationType());
		OperationResultType repoResult = repoAccountType.getResult();
		assertNotNull("No result in shadow (repo)", repoResult);
		assertFailure("Result in shadow (repo)", repoResult);

		AccountShadowType provisioningAccountType = provisioningService.getObject(AccountShadowType.class, ACCOUNT_MODIFY_OID,
				null, result).asObjectable();
		display("provisioning shadow", provisioningAccountType);
		assertEquals("Wrong failedOperationType in repo", FailedOperationTypeType.MODIFY, provisioningAccountType.getFailedOperationType());
		OperationResultType provisioningResult = provisioningAccountType.getResult();
		assertNotNull("No result in shadow (repo)", provisioningResult);
		assertFailure("Result in shadow (repo)", provisioningResult);
		
	}
	
	@Test
	public void test590Synchronize() throws Exception {
		final String TEST_NAME = "test590Synhronize";
		displayTestTile(TEST_NAME);
		// GIVEN
		Task task = taskManager.createTaskInstance(TestOpenDJNegative.class.getName()
				+ "." + TEST_NAME);
		OperationResult result = task.getResult();
		
		try {

			provisioningService.synchronize(RESOURCE_OPENDJ_OID, task, result);
			
			AssertJUnit.fail("addObject succeeded unexpectedly");
		} catch (CommunicationException e) {
			// This is expected
			display("Expected exception", e);
		}
		
		result.computeStatus();
		assertFailure(result);
	}

	
}