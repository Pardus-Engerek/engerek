/*
 * Copyright (c) 2012 Evolveum
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
 *
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.wf.processors.primary.user;

import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.ModelElementContext;
import com.evolveum.midpoint.model.lens.LensContext;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.dao.MiscDataUtil;
import com.evolveum.midpoint.wf.processes.CommonProcessVariableNames;
import com.evolveum.midpoint.wf.processes.addrole.AddRoleVariableNames;
import com.evolveum.midpoint.wf.processes.general.ApprovalRequest;
import com.evolveum.midpoint.wf.processes.general.ApprovalRequestImpl;
import com.evolveum.midpoint.wf.processes.general.Decision;
import com.evolveum.midpoint.wf.processes.general.ProcessVariableNames;
import com.evolveum.midpoint.wf.processors.primary.PrimaryChangeProcessor;
import com.evolveum.midpoint.wf.processors.primary.StartProcessInstructionForPrimaryStage;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.*;
import com.evolveum.midpoint.xml.ns.model.workflow.common_forms_2.RoleApprovalFormType;
import com.evolveum.prism.xml.ns._public.types_2.PolyStringType;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Process wrapper that manages role addition approval. It starts one process instance for each role
 * that has to be approved.
 *
 * In the past, we used to start one process instance for ALL roles to be approved. It made BPMN
 * approval process slightly more complex, while allowed to keep information about approval process
 * centralized from the user point of view (available via "single click"). If necessary, we can return
 * to this behavior.
 *
 * Alternatively, it is possible to start one process instance for a set of roles that share the
 * same approval mechanism. However, it is questionable what "the same approval mechanism" means,
 * for example, if there are expressions used to select an approver.
 *
 * @author mederly
 */
@Component("addRoleAssignmentWrapper")
public class AddRoleAssignmentWrapper extends AbstractUserWrapper {

    private static final Trace LOGGER = TraceManager.getTrace(AddRoleAssignmentWrapper.class);

    @Autowired
    private PrismContext prismContext;

    @Override
    public List<StartProcessInstructionForPrimaryStage> prepareProcessesToStart(ModelContext<?,?> modelContext, ObjectDelta<? extends ObjectType> change, Task task, OperationResult result) {

        List<ApprovalRequest<AssignmentType>> approvalRequestList = getAssignmentToApproveList(change, result);
        if (approvalRequestList == null) {
            return null;
        }

        return prepareStartProcessInstructions(modelContext, task, result, approvalRequestList);
    }

    private List<ApprovalRequest<AssignmentType>> getAssignmentToApproveList(ObjectDelta<? extends ObjectType> change, OperationResult result) {

        List<ApprovalRequest<AssignmentType>> approvalRequestList = new ArrayList<ApprovalRequest<AssignmentType>>();

        /*
         * We either add a user; then the list of roles to be added is given by the assignment property,
         * or we modify a user; then the list of roles is given by the assignment property modification.
         */

        if (change.getChangeType() == ChangeType.ADD) {

            PrismObject<?> prismToAdd = change.getObjectToAdd();
            UserType user = (UserType) prismToAdd.asObjectable();

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Role-related assignments in user add delta (" + user.getAssignment().size() + "): ");
            }

            Iterator<AssignmentType> assignmentTypeIterator = user.getAssignment().iterator();
            while (assignmentTypeIterator.hasNext()) {
                AssignmentType a = assignmentTypeIterator.next();
                ObjectReferenceType ort = a.getTargetRef();
                if (ort != null && RoleType.COMPLEX_TYPE.equals(ort.getType())) {
                    RoleType role = resolveRoleRef(a, result);
                    boolean approvalRequired = shouldRoleBeApproved(role);
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace(" - role: " + role + " (approval required = " + approvalRequired + ")");
                    }
                    if (approvalRequired) {
                        AssignmentType aCopy = a.clone();
                        aCopy.setTarget(role);
                        approvalRequestList.add(createApprovalRequest(aCopy, role));
                        assignmentTypeIterator.remove();
                    }
                }
            }

        } else if (change.getChangeType() == ChangeType.MODIFY) {

            Iterator<? extends ItemDelta> deltaIterator = change.getModifications().iterator();

            while (deltaIterator.hasNext()) {
                ItemDelta delta = deltaIterator.next();
                if (UserType.F_ASSIGNMENT.equals(delta.getName()) && delta.getValuesToAdd() != null && !delta.getValuesToAdd().isEmpty()) {          // todo: what if assignments are modified?

                    Iterator valueIterator = delta.getValuesToAdd().iterator();
                    while (valueIterator.hasNext()) {
                        Object o = valueIterator.next();
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Assignment to add = " + ((PrismContainerValue) o).dump());
                        }
                        PrismContainerValue<AssignmentType> at = (PrismContainerValue<AssignmentType>) o;
                        ObjectReferenceType ort = at.getValue().getTargetRef();
                        if (ort != null && RoleType.COMPLEX_TYPE.equals(ort.getType())) {
                            RoleType role = resolveRoleRef(at.getValue(), result);
                            boolean approvalRequired = shouldRoleBeApproved(role);
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace(" - role: " + role + " (approval required = " + approvalRequired + ")");
                            }
                            if (approvalRequired) {
                                AssignmentType aCopy = at.asContainerable().clone();
                                aCopy.setTarget(role);
                                approvalRequestList.add(createApprovalRequest(aCopy, role));
                                valueIterator.remove();
                            }
                        }
                    }
                    if (delta.getValuesToAdd().isEmpty()) {         // empty set of values to add is an illegal state
                        delta.resetValuesToAdd();
                        if (delta.getValuesToReplace() == null && delta.getValuesToDelete() == null) {
                            deltaIterator.remove();
                        }
                    }
                }
            }
        } else {
            return null;
        }
        return approvalRequestList;
    }

    private ApprovalRequest<AssignmentType> createApprovalRequest(AssignmentType a, RoleType role) {
        return new ApprovalRequestImpl(a, role.getApprovalSchema(), role.getApproverRef(), role.getApproverExpression(), role.getAutomaticallyApproved());
    }


    // approvalRequestList should contain de-referenced roles
    private List<StartProcessInstructionForPrimaryStage> prepareStartProcessInstructions(ModelContext<?, ?> modelContext, Task task, OperationResult result, List<ApprovalRequest<AssignmentType>> approvalRequestList) {
        List<StartProcessInstructionForPrimaryStage> instructions = new ArrayList<StartProcessInstructionForPrimaryStage>();

        ModelElementContext<UserType> fc = (ModelElementContext<UserType>) modelContext.getFocusContext();
        String userName = MiscDataUtil.getObjectName(modelContext);

        for (ApprovalRequest<AssignmentType> approvalRequest : approvalRequestList) {

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Approval request = " + approvalRequest);
            }

            AssignmentType assignmentType = approvalRequest.getItemToApprove();
            RoleType roleType = (RoleType) assignmentType.getTarget();

            Validate.notNull(roleType);
            Validate.notNull(roleType.getName());
            String roleName = roleType.getName().getOrig();

            String objectOid = getObjectOid(modelContext);
            PrismObject<UserType> requester = getRequester(task, result);

            StartProcessInstructionForPrimaryStage instruction = new StartProcessInstructionForPrimaryStage();

            prepareCommonInstructionAttributes(instruction, modelContext, objectOid, requester, task);
            instruction.addProcessVariable(AddRoleVariableNames.USER_NAME, userName);

            instruction.setProcessName(GENERAL_APPROVAL_PROCESS);
            instruction.setSimple(false);

            instruction.setExecuteImmediately(ModelExecuteOptions.isExecuteImmediatelyAfterApproval(((LensContext) modelContext).getOptions()));
            String andExecuting = instruction.isExecuteImmediately() ? "and executing " : "";
            instruction.setTaskName(new PolyStringType("Workflow for approving " + andExecuting + "adding " + roleName + " to " + userName));
            instruction.addProcessVariable(CommonProcessVariableNames.VARIABLE_PROCESS_NAME, "Adding " + roleName + " to " + userName);

            instruction.addProcessVariable(ProcessVariableNames.APPROVAL_REQUEST, approvalRequest);
            instruction.addProcessVariable(ProcessVariableNames.APPROVAL_TASK_NAME, "Approve adding " + roleName + " to " + userName);

            instruction.addProcessVariable(CommonProcessVariableNames.VARIABLE_MIDPOINT_ADDITIONAL_DATA, roleType);


            ObjectDelta<? extends ObjectType> delta = assignmentToDelta(modelContext, approvalRequest, objectOid);
            instruction.setDelta(delta);
            setDeltaProcessVariable(instruction, delta);

            instructions.add(instruction);
        }
        return instructions;
    }

    private ObjectDelta<? extends ObjectType> assignmentToDelta(ModelContext<?, ?> modelContext, ApprovalRequest<AssignmentType> approvalRequest, String objectOid) {
        PrismObject<UserType> user = (PrismObject<UserType>) modelContext.getFocusContext().getObjectNew();
        PrismContainerDefinition<AssignmentType> prismContainerDefinition = user.getDefinition().findContainerDefinition(UserType.F_ASSIGNMENT);

        ItemDelta<PrismContainerValue<AssignmentType>> addRoleDelta = new ContainerDelta<AssignmentType>(new ItemPath(), UserType.F_ASSIGNMENT, prismContainerDefinition);
        addRoleDelta.addValueToAdd(approvalRequest.getItemToApprove().asPrismContainerValue().clone());

        return ObjectDelta.createModifyDelta(objectOid != null ? objectOid : PrimaryChangeProcessor.UNKNOWN_OID, addRoleDelta, UserType.class, ((LensContext) modelContext).getPrismContext());
    }


    public static final String APPROVER_MAIL_ADDRESS = "approverMailAddress";

    private boolean shouldRoleBeApproved(RoleType role) {
        return !role.getApproverRef().isEmpty() || !role.getApproverExpression().isEmpty() || role.getApprovalSchema() != null;
    }

    private RoleType resolveRoleRef(AssignmentType a, OperationResult result) {
        RoleType role = (RoleType) a.getTarget();
        if (role == null) {
            try {
                role = repositoryService.getObject(RoleType.class, a.getTargetRef().getOid(), result).asObjectable();
            } catch (ObjectNotFoundException e) {
                throw new SystemException(e);
            } catch (SchemaException e) {
                throw new SystemException(e);
            }
            a.setTarget(role);
        }
        return role;
    }

    public static String formatTimeIntervalBrief(AssignmentType assignment) {
        StringBuilder sb = new StringBuilder();
        if (assignment != null && assignment.getActivation() != null &&
                (assignment.getActivation().getValidFrom() != null || assignment.getActivation().getValidTo() != null)) {
            if (assignment.getActivation().getValidFrom() != null && assignment.getActivation().getValidTo() != null) {
                sb.append(formatTime(assignment.getActivation().getValidFrom()));
                sb.append("-");
                sb.append(formatTime(assignment.getActivation().getValidTo()));
            } else if (assignment.getActivation().getValidFrom() != null) {
                sb.append("from ");
                sb.append(formatTime(assignment.getActivation().getValidFrom()));
            } else {
                sb.append("to ");
                sb.append(formatTime(assignment.getActivation().getValidTo()));
            }
        }
        return sb.toString();
    }

    private static String formatTime(XMLGregorianCalendar time) {
        DateFormat formatter = DateFormat.getDateInstance();
        return formatter.format(time.toGregorianCalendar().getTime());
    }

    @Override
    public PrismObject<? extends ObjectType> getRequestSpecificData(org.activiti.engine.task.Task task, Map<String, Object> variables, OperationResult result) {

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("getRequestSpecific starting: execution id " + task.getExecutionId() + ", pid " + task.getProcessInstanceId() + ", variables = " + variables);
        }

        PrismObjectDefinition<RoleApprovalFormType> formDefinition = prismContext.getSchemaRegistry().findObjectDefinitionByType(RoleApprovalFormType.COMPLEX_TYPE);
        PrismObject<RoleApprovalFormType> formPrism = formDefinition.instantiate();
        RoleApprovalFormType form = formPrism.asObjectable();

        form.setUser((String) variables.get(AddRoleVariableNames.USER_NAME));

        // todo check type compatibility
        ApprovalRequest request = (ApprovalRequest) variables.get(ProcessVariableNames.APPROVAL_REQUEST);
        Validate.notNull(request, "Approval request is not present among process variables");

        AssignmentType assignment = (AssignmentType) request.getItemToApprove();
        Validate.notNull(assignment, "Approval request does not contain as assignment");

        RoleType role = (RoleType) (assignment).getTarget();
        Validate.notNull(role, "Approval request does not contain role information");

        form.setRole(role.getName() == null ? role.getOid() : role.getName().getOrig());        // ==null should not occur
        form.setRequesterComment(assignment.getDescription());
        form.setTimeInterval(formatTimeIntervalBrief(assignment));

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Resulting prism object instance = " + formPrism.debugDump());
        }
        return formPrism;
    }


}