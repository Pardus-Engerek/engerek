/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.model;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditEventStage;
import com.evolveum.midpoint.audit.api.AuditEventType;
import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.ModelPort;
import com.evolveum.midpoint.model.controller.ModelController;
import com.evolveum.midpoint.model.scripting.Data;
import com.evolveum.midpoint.model.scripting.ExecutionContext;
import com.evolveum.midpoint.model.scripting.ScriptExecutionException;
import com.evolveum.midpoint.model.scripting.ScriptingExpressionEvaluator;
import com.evolveum.midpoint.model.util.Utils;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.QueryJaxbConvertor;
import com.evolveum.midpoint.provisioning.api.ChangeNotificationDispatcher;
import com.evolveum.midpoint.provisioning.api.ResourceEventDescription;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.MiscSchemaUtil;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.ExecuteScriptsOptionsType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.ObjectDeltaListType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.ObjectListType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.OutputFormatType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.ScriptOutputsType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.SelectorQualifiedGetOptionsType;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.SingleScriptOutputType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ModelExecuteOptionsType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.OperationResultType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceObjectShadowChangeDescriptionType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.TaskType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;
import com.evolveum.midpoint.xml.ns._public.common.fault_1.FaultType;
import com.evolveum.midpoint.xml.ns._public.common.fault_1.IllegalArgumentFaultType;
import com.evolveum.midpoint.xml.ns._public.common.fault_1.ObjectAlreadyExistsFaultType;
import com.evolveum.midpoint.xml.ns._public.common.fault_1.ObjectNotFoundFaultType;
import com.evolveum.midpoint.xml.ns._public.common.fault_1.SystemFaultType;
import com.evolveum.midpoint.xml.ns._public.common.fault_1_wsdl.FaultMessage;
import com.evolveum.midpoint.xml.ns._public.model.model_1.ExecuteScripts;
import com.evolveum.midpoint.xml.ns._public.model.model_1.ExecuteScriptsResponse;
import com.evolveum.midpoint.xml.ns._public.model.model_1_wsdl.ModelPortType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_2.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.model.scripting_2.ItemListType;
import com.evolveum.prism.xml.ns._public.query_2.PagingType;
import com.evolveum.prism.xml.ns._public.query_2.QueryType;
import com.evolveum.prism.xml.ns._public.types_2.ObjectDeltaType;
import com.evolveum.prism.xml.ns._public.types_2.PolyStringType;
import com.evolveum.prism.xml.ns._public.types_2.RawType;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.xml.namespace.QName;
import javax.xml.ws.Holder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 
 * @author lazyman
 * 
 */
@Service
public class ModelWebService implements ModelPortType, ModelPort {

	private static final Trace LOGGER = TraceManager.getTrace(ModelWebService.class);
	
	@Autowired(required = true)
	private ModelCrudService model;
	
    // for more complicated interactions (like executeChanges)
    @Autowired
    private ModelController modelController;
	
	@Autowired(required = true)
	private TaskManager taskManager;
	
	@Autowired(required = true)
	private AuditService auditService;
	
	@Autowired(required = true)
	private PrismContext prismContext;
	
    @Autowired
    private ScriptingExpressionEvaluator scriptingExpressionEvaluator;

	@Override
	public void getObject(QName objectType, String oid, SelectorQualifiedGetOptionsType optionsType,
			Holder<ObjectType> objectHolder, Holder<OperationResultType> resultHolder) throws FaultMessage {
        notNullArgument(objectType, "Object type must not be null.");
		notEmptyArgument(oid, "Oid must not be null or empty.");

		Task task = createTaskInstance(GET_OBJECT);
		auditLogin(task);
		OperationResult operationResult = task.getResult();
		try {
            Class objectClass = ObjectTypes.getObjectTypeFromTypeQName(objectType).getClassDefinition();
            Collection<SelectorOptions<GetOperationOptions>> options = MiscSchemaUtil.optionsTypeToOptions(optionsType);
            PrismObject<? extends ObjectType> object = model.getObject(objectClass, oid, options, task, operationResult);
			handleOperationResult(operationResult, resultHolder);
			objectHolder.value = object.asObjectable();
			return;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "# MODEL getObject() failed", ex);
			auditLogout(task);
			throw createSystemFault(ex, operationResult);
		}
	}

	@Override
	public void searchObjects(QName objectType, QueryType query, SelectorQualifiedGetOptionsType optionsType,
                  Holder<ObjectListType> objectListHolder, Holder<OperationResultType> result) throws FaultMessage {
        notNullArgument(objectType, "Object type must not be null.");

		Task task = createTaskInstance(SEARCH_OBJECTS);
		auditLogin(task);
		OperationResult operationResult = task.getResult();
		try {
            Class objectClass = ObjectTypes.getObjectTypeFromTypeQName(objectType).getClassDefinition();
            Collection<SelectorOptions<GetOperationOptions>> options = MiscSchemaUtil.optionsTypeToOptions(optionsType);
			ObjectQuery q = QueryJaxbConvertor.createObjectQuery(objectClass, query, prismContext);
			List<PrismObject<? extends ObjectType>> list = (List)model.searchObjects(objectClass, q, options, task, operationResult);
			handleOperationResult(operationResult, result);
			ObjectListType listType = new ObjectListType();
			for (PrismObject<? extends ObjectType> o : list) {
				listType.getObject().add(o.asObjectable());
			}
			objectListHolder.value = listType;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "# MODEL searchObjects() failed", ex);
			auditLogout(task);
			throw createSystemFault(ex, operationResult);
		}
	}

    @Override
    public OperationResultType executeChanges(ObjectDeltaListType deltaList, ModelExecuteOptionsType optionsType) throws FaultMessage {
		notNullArgument(deltaList, "Object delta list must not be null.");

		Task task = createTaskInstance(EXECUTE_CHANGES);
		auditLogin(task);
		OperationResult operationResult = task.getResult();
		try {
			Collection<ObjectDelta> deltas = DeltaConvertor.createObjectDeltas(deltaList, prismContext);
            ModelExecuteOptions options = ModelExecuteOptions.fromModelExecutionOptionsType(optionsType);
            modelController.executeChanges((Collection) deltas, options, task, operationResult);        // brutally eliminating type-safety compiler barking
			return handleOperationResult(operationResult);
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "# MODEL executeChanges() failed", ex);
			auditLogout(task);
			throw createSystemFault(ex, operationResult);
		}
	}

	@Override
	public void findShadowOwner(String accountOid, Holder<UserType> userHolder, Holder<OperationResultType> result)
			throws FaultMessage {
		notEmptyArgument(accountOid, "Account oid must not be null or empty.");

		Task task = createTaskInstance(LIST_ACCOUNT_SHADOW_OWNER);
		auditLogin(task);
		OperationResult operationResult = task.getResult();
		try {
			PrismObject<UserType> user = model.findShadowOwner(accountOid, task, operationResult);
			handleOperationResult(operationResult, result);
			if (user != null) {
				userHolder.value = user.asObjectable();
			}
			return;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "# MODEL findShadowOwner() failed", ex);
			auditLogout(task);
			throw createSystemFault(ex, operationResult);
		}
	}

	@Override
	public OperationResultType testResource(String resourceOid) throws FaultMessage {
		notEmptyArgument(resourceOid, "Resource oid must not be null or empty.");

		Task task = createTaskInstance(TEST_RESOURCE);
		auditLogin(task);
		try {
			OperationResult testResult = model.testResource(resourceOid, task);
			return handleOperationResult(testResult);
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "# MODEL testResource() failed", ex);
			auditLogout(task);
			throw createSystemFault(ex, null);
		}
	}

    @Override
    public ExecuteScriptsResponse executeScripts(ExecuteScripts parameters) throws FaultMessage {
        Task task = createTaskInstance(EXECUTE_SCRIPTS);
        auditLogin(task);
        OperationResult result = task.getResult();
        try {
            List<ExpressionType> scriptsToExecute = parseScripts(parameters);
            return doExecuteScripts(scriptsToExecute, parameters.getOptions(), task, result);
        } catch (Exception ex) {
            LoggingUtils.logException(LOGGER, "# MODEL executeScripts() failed", ex);
            auditLogout(task);
            throw createSystemFault(ex, null);
        }
    }

    private List<ExpressionType> parseScripts(ExecuteScripts parameters) throws JAXBException, SchemaException {
        List<ExpressionType> scriptsToExecute = new ArrayList<>();
        if (parameters.getXmlScripts() != null) {
            for (Object scriptAsObject : parameters.getXmlScripts().getAny()) {
                if (scriptAsObject instanceof ExpressionType) {
                    scriptsToExecute.add((ExpressionType) scriptAsObject);
                } else {
                    throw new IllegalArgumentException("Invalid script type: " + scriptAsObject.getClass());
                }
            }
        } else {
            // here comes MSL script decoding (however with a quick hack to allow passing XML as text here)
            String scriptsAsString = parameters.getMslScripts();
            if (scriptsAsString.startsWith("<?xml")) {
                ExpressionType expressionType = prismContext.parsePrismPropertyRealValue(scriptsAsString, ExpressionType.COMPLEX_TYPE, PrismContext.LANG_XML);
                scriptsToExecute.add(expressionType);
            }
        }
        return scriptsToExecute;
    }

    private ExecuteScriptsResponse doExecuteScripts(List<ExpressionType> scriptsToExecute, ExecuteScriptsOptionsType options, Task task, OperationResult result) throws ScriptExecutionException, JAXBException, SchemaException {
        ExecuteScriptsResponse response = new ExecuteScriptsResponse();
        ScriptOutputsType outputs = new ScriptOutputsType();
        response.setOutputs(outputs);

        try {
            for (ExpressionType script : scriptsToExecute) {

                ExecutionContext outputContext = scriptingExpressionEvaluator.evaluateExpression(script, task, result);

                SingleScriptOutputType output = new SingleScriptOutputType();
                outputs.getOutput().add(output);

                output.setTextOutput(outputContext.getConsoleOutput());
                if (options == null || options.getOutputFormat() == null || options.getOutputFormat() == OutputFormatType.XML) {
                    output.setXmlData(prepareXmlData(outputContext.getFinalOutput()));
                } else {
                    // temporarily we send serialized XML in the case of MSL output
                    ItemListType jaxbOutput = prepareXmlData(outputContext.getFinalOutput());
                    output.setMslData(prismContext.serializePrismPropertyRealValues(SchemaConstants.APIT_ITEM_LIST, PrismContext.LANG_XML, jaxbOutput));
                }
            }
            result.computeStatusIfUnknown();
        } catch (Exception e) {         // FIXME little bit brutal treatment
            result.recordFatalError(e.getMessage(), e);
        }
        result.summarize();
        response.setResult(result.createOperationResultType());
        return response;
    }

    private ItemListType prepareXmlData(Data output) throws JAXBException, SchemaException {
        ItemListType itemListType = new ItemListType();
        if (output != null) {
            for (Item item : output.getData()) {
                RawType rawType = prismContext.toRawType(item);
                itemListType.getItem().add(rawType);
            }
        }
        return itemListType;
    }


	private void handleOperationResult(OperationResult result, Holder<OperationResultType> holder) {
		result.recordSuccess();
		OperationResultType resultType = result.createOperationResultType();
		if (holder.value == null) {
			holder.value = resultType;
		} else {
			holder.value.getPartialResults().add(resultType);
		}
	}

	private OperationResultType handleOperationResult(OperationResult result) {
		result.recordSuccess();
		return result.createOperationResultType();
	}
	
	private void notNullResultHolder(Holder<OperationResultType> holder) throws FaultMessage {
		notNullArgument(holder, "Holder must not be null.");
		notNullArgument(holder.value, "Result type must not be null.");
	}

	private <T> void notNullHolder(Holder<T> holder) throws FaultMessage {
		notNullArgument(holder, "Holder must not be null.");
		notNullArgument(holder.value, holder.getClass().getSimpleName() + " must not be null (in Holder).");
	}

	private void notEmptyArgument(String object, String message) throws FaultMessage {
		if (StringUtils.isEmpty(object)) {
			throw createIllegalArgumentFault(message);
		}
	}

	private void notNullArgument(Object object, String message) throws FaultMessage {
		if (object == null) {
			throw createIllegalArgumentFault(message);
		}
	}

	private FaultMessage createIllegalArgumentFault(String message) {
		FaultType faultType = new IllegalArgumentFaultType();
		return new FaultMessage(message, faultType);
	}

	private FaultMessage createSystemFault(Exception ex, OperationResult result) {
		if (result != null) {
			result.recordFatalError(ex.getMessage(), ex);
		}

		FaultType faultType;
		if (ex instanceof ObjectNotFoundException) {
			faultType = new ObjectNotFoundFaultType();
		} else if (ex instanceof IllegalArgumentException) {
			faultType = new IllegalArgumentFaultType();
		} else if (ex instanceof ObjectAlreadyExistsException){
			faultType = new ObjectAlreadyExistsFaultType();
		} else{
			faultType = new SystemFaultType();
		}
		faultType.setMessage(ex.getMessage());
		if (result != null) {
			faultType.setOperationResult(result.createOperationResultType());
		}

		return new FaultMessage(ex.getMessage(), faultType, ex);
	}

	@Override
	public TaskType importFromResource(String resourceOid, QName objectClass)
			throws FaultMessage {
		notEmptyArgument(resourceOid, "Resource oid must not be null or empty.");
		notNullArgument(objectClass, "Object class must not be null.");

		Task task = createTaskInstance(IMPORT_FROM_RESOURCE);
		auditLogin(task);
		OperationResult operationResult = task.getResult();

		try {
			model.importFromResource(resourceOid, objectClass, task, operationResult);
			operationResult.computeStatus();
			return handleTaskResult(task);
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "# MODEL importFromResource() failed", ex);
			auditLogout(task);
			throw createSystemFault(ex, operationResult);
		}
	}
	
	@Override
	public TaskType notifyChange(ResourceObjectShadowChangeDescriptionType changeDescription)
			throws FaultMessage {
		// TODO Auto-generated method stub
		notNullArgument(changeDescription, "Change description must not be null");
		LOGGER.trace("notify change started");
		
		Task task = createTaskInstance(NOTIFY_CHANGE);
		OperationResult parentResult = task.getResult();
		
		try {
			model.notifyChange(changeDescription, parentResult, task);
			} catch (ObjectNotFoundException ex) {
				LoggingUtils.logException(LOGGER, "# MODEL notifyChange() failed", ex);
				auditLogout(task);
				throw createSystemFault(ex, parentResult);
			} catch (SchemaException ex) {
				 LoggingUtils.logException(LOGGER, "# MODEL notifyChange() failed", ex);
				auditLogout(task);
				throw createSystemFault(ex, parentResult);
			} catch (CommunicationException ex) {
				LoggingUtils.logException(LOGGER, "# MODEL notifyChange() failed", ex);
				auditLogout(task);
				throw createSystemFault(ex, parentResult);
			} catch (ConfigurationException ex) {
				LoggingUtils.logException(LOGGER, "# MODEL notifyChange() failed", ex);
				auditLogout(task);
				throw createSystemFault(ex, parentResult);
			} catch (SecurityViolationException ex) {
				LoggingUtils.logException(LOGGER, "# MODEL notifyChange() failed", ex);
				auditLogout(task);
				throw createSystemFault(ex, parentResult);
			} catch (RuntimeException ex){
				LoggingUtils.logException(LOGGER, "# MODEL notifyChange() failed", ex);
				auditLogout(task);
				throw createSystemFault(ex, parentResult);
			} catch (ObjectAlreadyExistsException ex){
				LoggingUtils.logException(LOGGER, "# MODEL notifyChange() failed", ex);
				auditLogout(task);
				throw createSystemFault(ex, parentResult);
			}
		
		
		LOGGER.info("notify change ended.");
		LOGGER.info("result of notify change: {}", parentResult.debugDump());
		return handleTaskResult(task);
	}


    private void setTaskOwner(Task task) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new SystemException("Failed to get authentication object");
        }
        UserType userType = (UserType) ((MidPointPrincipal)(SecurityContextHolder.getContext().getAuthentication().getPrincipal())).getUser();
        if (userType == null) {
            throw new SystemException("Failed to get user from authentication object");
        }
        task.setOwner(userType.asPrismObject());
    }

    private Task createTaskInstance(String operationName) {
		// TODO: better task initialization
		Task task = taskManager.createTaskInstance(operationName);
		setTaskOwner(task);
		task.setChannel(SchemaConstants.CHANNEL_WEB_SERVICE_URI);
		return task;
	}
	
	/**
	 * return appropriate form of taskType (and result) to
	 * return back to a web service caller.
	 * 
	 * @param task
	 */
	private TaskType handleTaskResult(Task task) {
		return task.getTaskPrismObject().asObjectable();
	}
	
	private void auditLogin(Task task) {
        AuditEventRecord record = new AuditEventRecord(AuditEventType.CREATE_SESSION, AuditEventStage.REQUEST);
        PrismObject<UserType> owner = task.getOwner();
        if (owner != null) {
	        record.setInitiator(owner);
	        PolyStringType name = owner.asObjectable().getName();
	        if (name != null) {
	        	record.setParameter(name.getOrig());
	        }
        }

        record.setChannel(SchemaConstants.CHANNEL_WEB_SERVICE_URI);
        record.setTimestamp(System.currentTimeMillis());
        record.setSessionIdentifier(task.getTaskIdentifier());
        
        record.setOutcome(OperationResultStatus.SUCCESS);

        auditService.audit(record, task);
	}
	
	private void auditLogout(Task task) {
		AuditEventRecord record = new AuditEventRecord(AuditEventType.TERMINATE_SESSION, AuditEventStage.REQUEST);
		PrismObject<UserType> owner = task.getOwner();
        if (owner != null) {
	        record.setInitiator(owner);
	        PolyStringType name = owner.asObjectable().getName();
	        if (name != null) {
	        	record.setParameter(name.getOrig());
	        }
        }

        record.setChannel(SchemaConstants.CHANNEL_WEB_SERVICE_URI);
        record.setTimestamp(System.currentTimeMillis());
        record.setSessionIdentifier(task.getTaskIdentifier());
        
        record.setOutcome(OperationResultStatus.SUCCESS);

        auditService.audit(record, task);
	}
}
