/**
 * Copyright (c) 2010-2016 Evolveum
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
package com.evolveum.midpoint.web.page.admin.users.component;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.AjaxSubmitButton;
import com.evolveum.midpoint.web.component.form.Form;
import com.evolveum.midpoint.web.component.input.DropDownChoicePanel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MergeConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by honchar.
 */
public class MergeObjectsPanel<F extends FocusType> extends BasePanel{
    private static final Trace LOGGER = TraceManager.getTrace(MergeObjectsPanel.class);
    private static final String DOT_CLASS = MergeObjectsPanel.class.getName() + ".";
    private static final String OPERATION_GET_MERGE_OBJECT_PREVIEW = DOT_CLASS + "getMergeObjectPreview";
    private static final String OPERATION_LOAD_MERGE_TYPE_NAMES = DOT_CLASS + "loadMergeTypeNames";


    private static final String ID_MERGE_OBJECT_DETAILS_PANEL = "mergeObjectDetailsPanel";
    private static final String ID_MERGE_WITH_OBJECT_DETAILS_PANEL = "mergeWithObjectDetailsPanel";
    private static final String ID_MERGE_RESULT_OBJECT_DETAILS_PANEL = "mergeResultObjectDetailsPanel";
    private static final String ID_BACK_BUTTON = "back";
    private static final String ID_SWITCH_DIRECTION_BUTTON = "switchDirection";
    private static final String ID_MERGE_DELTA_PREVIEW_BUTTON = "mergeDeltaPreview";
    private static final String ID_MERGE_BUTTON = "merge";
    private static final String ID_FORM = "mainForm";
    private static final String ID_MERGE_TYPE_SELECTOR = "mergeType";

    private F mergeObject;
    private F mergeWithObject;
    private Class<F> type;
    private PageBase pageBase;
    private IModel<String> mergeTypeModel;
    private String currentMergeType = "";
    private IModel<List<String>> mergeTypeChoicesModel;
    private List<String> mergeTypeChoices;

    public MergeObjectsPanel(String id){
        super(id);
    }

    public MergeObjectsPanel(String id, F mergeObject, F mergeWithObject, Class<F> type, PageBase pageBase){
        super(id);
        this.mergeObject = mergeObject;
        this.mergeWithObject = mergeWithObject;
        this.type = type;
        this.pageBase = pageBase;
        mergeTypeChoices = getMergeTypeNames();

        initModels();
        initLayout();
    }

    private void initModels(){
        mergeTypeModel = new IModel<String>() {
            @Override
            public String getObject() {
                return currentMergeType;
            }

            @Override
            public void setObject(String mergeType) {
                currentMergeType = mergeType;
            }

            @Override
            public void detach() {

            }
        };

        mergeTypeChoicesModel = new IModel<List<String>>() {
            @Override
            public List<String> getObject() {
                return mergeTypeChoices;
            }

            @Override
            public void setObject(List<String> strings) {

            }

            @Override
            public void detach() {

            }
        };
    }

    private void initLayout(){
        Form mainForm =  new Form(ID_FORM);
        mainForm.setOutputMarkupId(true);
        add(mainForm);

        DropDownChoicePanel mergeTypeSelect = new DropDownChoicePanel(ID_MERGE_TYPE_SELECTOR,
                mergeTypeModel, mergeTypeChoicesModel);

//                WebComponentUtil.createEnumPanel(MergeType.class,
//                ID_MERGE_TYPE_SELECTOR, mergeTypeModel, this);
//        mergeTypeSelect.add(new OnChangeAjaxBehavior() {
//
//            @Override
//            protected void onUpdate(AjaxRequestTarget target) {
////                AssignmentCatalogPanel parentPanel = CatalogItemsPanel.this.findParent(AssignmentCatalogPanel.class);
////                parentPanel.addOrReplaceLayout();
////                target.add(parentPanel);
//            }
//        });
        mergeTypeSelect.setOutputMarkupId(true);
        mainForm.add(mergeTypeSelect);

        MergeObjectDetailsPanel mergeObjectPanel = new MergeObjectDetailsPanel(ID_MERGE_OBJECT_DETAILS_PANEL,
                mergeObject, type);
        mergeObjectPanel.setOutputMarkupId(true);
        mainForm.add(mergeObjectPanel);

        MergeObjectDetailsPanel mergeWithObjectPanel = new MergeObjectDetailsPanel(ID_MERGE_WITH_OBJECT_DETAILS_PANEL,
                mergeWithObject, type);
        mergeWithObjectPanel.setOutputMarkupId(true);
        mainForm.add(mergeWithObjectPanel);

        PrismObject<F> mergeResultObject = getMergeObjectsResult();
        Component mergeObjectsResultPanel;
        if (mergeResultObject != null) {
            mergeObjectsResultPanel = new MergeObjectDetailsPanel(ID_MERGE_RESULT_OBJECT_DETAILS_PANEL,
                    mergeResultObject.asObjectable(), type);
        } else {
            mergeObjectsResultPanel = new Label(ID_MERGE_RESULT_OBJECT_DETAILS_PANEL,
                    pageBase.createStringResource("PageMergeObjects.noMergeResultObjectWarning"));
        }
        mergeObjectsResultPanel.setOutputMarkupId(true);
        mainForm.add(mergeObjectsResultPanel);

        initButtonPanel(mainForm);
    }

    private void initButtonPanel(Form mainForm){
        AjaxSubmitButton switchDirectionButton = new AjaxSubmitButton(ID_SWITCH_DIRECTION_BUTTON, pageBase.createStringResource("PageMergeObjects.switchDirectionButton")) {

            @Override
            protected void onSubmit(AjaxRequestTarget target,
                                    org.apache.wicket.markup.html.form.Form<?> form) {
            }

            @Override
            protected void onError(AjaxRequestTarget target,
                                   org.apache.wicket.markup.html.form.Form<?> form) {
                target.add(pageBase.getFeedbackPanel());
            }
        };
        mainForm.add(switchDirectionButton);

        AjaxSubmitButton mergeDeltaPreviewButton = new AjaxSubmitButton(ID_MERGE_DELTA_PREVIEW_BUTTON,
                pageBase.createStringResource("PageMergeObjects.mergeDeltaPreviewButton")) {

            @Override
            protected void onSubmit(AjaxRequestTarget target,
                                    org.apache.wicket.markup.html.form.Form<?> form) {
            }

            @Override
            protected void onError(AjaxRequestTarget target,
                                   org.apache.wicket.markup.html.form.Form<?> form) {
                target.add(pageBase.getFeedbackPanel());
            }
        };
        mainForm.add(mergeDeltaPreviewButton);

        AjaxSubmitButton mergeButton = new AjaxSubmitButton(ID_MERGE_BUTTON,
                pageBase.createStringResource("PageMergeObjects.mergeButton")) {

            @Override
            protected void onSubmit(AjaxRequestTarget target,
                                    org.apache.wicket.markup.html.form.Form<?> form) {
            }

            @Override
            protected void onError(AjaxRequestTarget target,
                                   org.apache.wicket.markup.html.form.Form<?> form) {
                target.add(pageBase.getFeedbackPanel());
            }
        };
        mainForm.add(mergeButton);

        AjaxButton back = new AjaxButton(ID_BACK_BUTTON, pageBase.createStringResource("PageMergeObjects.backButton")) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                pageBase.redirectBack();
            }

        };
        mainForm.add(back);

    }

    private List<String> getMergeTypeNames(){
        List<String> mergeTypeNamesList = new ArrayList<>();
        Task task = pageBase.createAnonymousTask(OPERATION_LOAD_MERGE_TYPE_NAMES);
        OperationResult result = task.getResult();

        PrismObject<SystemConfigurationType> config;
        try {
            config = pageBase.getModelService().getObject(SystemConfigurationType.class,
                    SystemObjectsType.SYSTEM_CONFIGURATION.value(), null, task, result);
        } catch (ObjectNotFoundException | SchemaException | SecurityViolationException
                | CommunicationException | ConfigurationException e) {
            LOGGER.error("Error getting system configuration: {}", e.getMessage(), e);
            return null;
        }
        if (config != null && config.asObjectable() != null){
            List<MergeConfigurationType> list = config.asObjectable().getMergeConfiguration();
            if (list != null) {
                for (MergeConfigurationType mergeType : list) {
                    mergeTypeNamesList.add(mergeType.getName());
                }
                if (mergeTypeNamesList.size() > 0){
                    currentMergeType = mergeTypeNamesList.get(0);
                }
            }
        }
        return mergeTypeNamesList;
    }
    private PrismObject<F> getMergeObjectsResult(){
        OperationResult result = new OperationResult(OPERATION_GET_MERGE_OBJECT_PREVIEW);
        PrismObject<F> mergeResultObject = null;
        try {
            Task task = pageBase.createSimpleTask(OPERATION_GET_MERGE_OBJECT_PREVIEW);
            mergeResultObject = pageBase.getModelInteractionService().mergeObjectsPreviewObject(type,
                    mergeObject.getOid(), mergeWithObject.getOid(), currentMergeType, task, result);
        } catch (Exception ex) {
            result.recomputeStatus();
            result.recordFatalError("Couldn't get merge object for preview.", ex);
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't get merge object for preview", ex);
            pageBase.showResult(result);
        }
        return mergeResultObject;
    }
}
