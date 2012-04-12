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

package com.evolveum.midpoint.web.page.admin.users;

import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.web.component.accordion.Accordion;
import com.evolveum.midpoint.web.component.accordion.AccordionItem;
import com.evolveum.midpoint.web.component.accordion.AccordionListView;
import com.evolveum.midpoint.web.component.button.AjaxLinkButton;
import com.evolveum.midpoint.web.component.button.AjaxSubmitLinkButton;
import com.evolveum.midpoint.web.component.objectform.ContainerStatus;
import com.evolveum.midpoint.web.component.objectform.ContainerWrapper;
import com.evolveum.midpoint.web.component.objectform.PrismFormPanel;
import com.evolveum.midpoint.web.component.prism.ObjectWrapper;
import com.evolveum.midpoint.web.component.prism.PrismObjectPanel;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.security.MidPointApplication;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserType;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.string.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lazyman
 */
public class PageUser extends PageAdminUsers {

    public static final String PARAM_USER_ID = "userId";

    private IModel<ContainerWrapper> model;

    public PageUser() {
        model = new LoadableModel<ContainerWrapper>(false) {

            @Override
            protected ContainerWrapper load() {
                return loadUser();
            }
        };

        initLayout();
    }

    private ContainerWrapper loadUser() {
        StringValue userOid = getPageParameters().get(PARAM_USER_ID);

        try {
            if (userOid == null || StringUtils.isEmpty(userOid.toString())) {
                return createNewUser();
            }

            MidPointApplication application = PageUser.this.getMidpointApplication();
            ModelService model = application.getModel();

            OperationResult result = new OperationResult("aaaaaaaaaaaaaaaa");
            PrismObject<UserType> object = model.getObject(UserType.class, userOid.toString(), null, result);

            return new ContainerWrapper(object, ContainerStatus.MODIFYING);
        } catch (Exception ex) {
            ex.printStackTrace();
            //todo error handling
        }

        try {
            return createNewUser();
        } catch (SchemaException ex) {
            ex.printStackTrace();
            //todo error handling
            throw new RestartResponseException(PageUsers.class);
        }
    }

    private ContainerWrapper createNewUser() throws SchemaException {
        UserType user = new UserType();

        MidPointApplication application = getMidpointApplication();
        application.getPrismContext().adopt(user);
        return new ContainerWrapper(user.asPrismObject(), ContainerStatus.ADDING);
    }

    private IModel<ObjectWrapper> loadTestWrapper() {
        return new LoadableModel<ObjectWrapper>(false) {

            @Override
            protected ObjectWrapper load() {
                PrismObject<UserType> user = null;
                try {
                    MidPointApplication application = PageUser.this.getMidpointApplication();

                    StringValue userOid = getPageParameters().get(PARAM_USER_ID);
                    if (userOid == null || StringUtils.isEmpty(userOid.toString())) {
                        UserType userType = new UserType();
                        application.getPrismContext().adopt(userType);
                        user = userType.asPrismObject();
                    } else {
                        ModelService model = application.getModel();

                        OperationResult result = new OperationResult("aaaaaaaaaaaaaaaa");
                        user = model.getObject(UserType.class, userOid.toString(), null, result);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                if (user == null) {
                    throw new IllegalArgumentException("ffffffffffuuuuuuuu");
                }

                return new ObjectWrapper("header text", "header description", user,
                        com.evolveum.midpoint.web.component.prism.ContainerStatus.MODIFYING);
            }
        };
    }

    private void initLayout() {
        Form mainForm = new Form("mainForm");
        add(mainForm);

        PrismObjectPanel test = new PrismObjectPanel("test", loadTestWrapper(), null);
        mainForm.add(test);

//        PrismFormPanel userForm = new PrismFormPanel("userForm", model);
//        mainForm.add(userForm);
        PrismObjectPanel userForm = new PrismObjectPanel("userForm", loadTestWrapper(), null);
        mainForm.add(userForm);

        Accordion accordion = new Accordion("accordion");
        accordion.setMultipleSelect(true);
        accordion.setOpenedPanel(0);
        mainForm.add(accordion);

        AccordionItem accounts = new AccordionItem("accounts", createStringResource("pageUser.accounts"));
        accordion.getBodyContainer().add(accounts);
        initAccounts(accounts);

        AccordionItem roles = new AccordionItem("roles", createStringResource("pageUser.roles"));
        accordion.getBodyContainer().add(roles);
        initRoles(roles);

        AccordionItem assignments = new AccordionItem("assignments", createStringResource("pageUser.assignments"));
        accordion.getBodyContainer().add(assignments);
        initAssignments(assignments);

        initButtons(mainForm);
    }

    private void initAccounts(AccordionItem accounts) {
        AccordionListView<ContainerWrapper> accountsAccordion = new AccordionListView<ContainerWrapper>(
                "accountsAccordion", createAccountsModel()) {

            @Override
            protected Component createPanelBody(String componentId, IModel<ContainerWrapper> itemModel) {
                PrismObjectPanel acc = new PrismObjectPanel(componentId, loadTestWrapper(), null);
                acc.setShowHeader(false);

                return acc;
            }

            @Override
            protected IModel<String> createHeaderLabel(IModel<ContainerWrapper> itemModel) {
                return new Model<String>("asdf");
            }
        };
        accountsAccordion.setMultipleSelect(true);
        accounts.getBodyContainer().add(accountsAccordion);
    }

    private IModel<List<ContainerWrapper>> createAccountsModel() {
        return new LoadableModel<List<ContainerWrapper>>(false) {

            @Override
            protected List<ContainerWrapper> load() {
                List<ContainerWrapper> list = new ArrayList<ContainerWrapper>();
                list.add(model.getObject());
                list.add(model.getObject());

                return list;
            }
        };
    }

    private void initRoles(AccordionItem roles) {
        //todo implement
    }

    private void initAssignments(AccordionItem assignments) {
        //todo implement
    }

    private void initButtons(Form mainForm) {
        AjaxSubmitLinkButton save = new AjaxSubmitLinkButton("save",
                createStringResource("pageUser.button.save")) {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                //todo implement
            }

            @Override
            protected void onError(AjaxRequestTarget target, Form<?> form) {
                //todo implement
            }
        };
        mainForm.add(save);

        AjaxLinkButton recalculate = new AjaxLinkButton("recalculate",
                createStringResource("pageUser.button.recalculate")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                //todo implement
            }
        };
        mainForm.add(recalculate);

        AjaxLinkButton refresh = new AjaxLinkButton("refresh",
                createStringResource("pageUser.button.refresh")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                //todo implement
            }
        };
        mainForm.add(refresh);

        AjaxLinkButton cancel = new AjaxLinkButton("cancel",
                createStringResource("pageUser.button.cancel")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                //todo implement
            }
        };
        mainForm.add(cancel);
    }
}
