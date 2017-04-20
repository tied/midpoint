/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.certification.impl;

import com.evolveum.midpoint.certification.api.AccessCertificationEventListener;
import com.evolveum.midpoint.certification.api.CertificationManager;
import com.evolveum.midpoint.certification.api.OutcomeUtils;
import com.evolveum.midpoint.certification.impl.handlers.CertificationHandler;
import com.evolveum.midpoint.model.api.ModelAuthorizationAction;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.CertCampaignTypeUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.security.api.SecurityEnforcer;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType.*;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignType.F_CASE;

/**
 * All operations carried out by CertificationManager have to be authorized by it. ModelController does NOT execute
 * any authorizations before passing method calls to this module.
 *
 * All repository read operations are invoked on repository cache (even if we currently don't enter the cache).
 *
 * All write operations are passed to model, with the flag of preAuthorized set to TRUE (and currently in raw mode).
 * The reason is that we want the changes to be audited.
 *
 * In the future, the raw mode could be eventually changed to non-raw, in order to allow e.g. lower-level notifications
 * to be sent (that means, for example, notifications related to changing certification campaign as a result of carrying
 * out open/close stage operations). But currently we are satisfied with notifications that are emitted by the
 * certification module itself.
 *
 * Also, in the future, we could do some higher-level audit by this module. But for now we are OK with the lower-level
 * audit generated by the model.
 *
 * TODO: consider the enormous size of audit events in case of big campaigns (e.g. thousands or tens of thousands
 * certification cases).
 *
 * We try to carry out repo update in one operation to ensure atomicity; unless we need to carry out them on separate
 * objects (e.g. certification definition + certification campaign).
 *
 * Methods in this module (e.g. searchCases) are to be called from outside only, as they carry out the authorization.
 * For pre-authorized versions please see various helpers, e.g. AccCertQueryHelper.
 *
 * @author mederly
 */
@Service(value = "certificationManager")
public class CertificationManagerImpl implements CertificationManager {

    private static final transient Trace LOGGER = TraceManager.getTrace(CertificationManager.class);

    public static final String INTERFACE_DOT = CertificationManager.class.getName() + ".";
    public static final String CLASS_DOT = CertificationManagerImpl.class.getName() + ".";
    public static final String OPERATION_CREATE_CAMPAIGN = INTERFACE_DOT + "createCampaign";
    public static final String OPERATION_OPEN_NEXT_STAGE = INTERFACE_DOT + "openNextStage";
    public static final String OPERATION_CLOSE_CURRENT_STAGE = INTERFACE_DOT + "closeCurrentStage";
    public static final String OPERATION_RECORD_DECISION = INTERFACE_DOT + "recordDecision";
    public static final String OPERATION_SEARCH_DECISIONS = INTERFACE_DOT + "searchOpenWorkItems";
    public static final String OPERATION_SEARCH_OPEN_WORK_ITEMS = INTERFACE_DOT + "searchOpenWorkItems";
    public static final String OPERATION_CLOSE_CAMPAIGN = INTERFACE_DOT + "closeCampaign";
    public static final String OPERATION_DELEGATE_WORK_ITEMS = INTERFACE_DOT + "delegateWorkItems";
    public static final String OPERATION_GET_CAMPAIGN_STATISTICS = INTERFACE_DOT + "getCampaignStatistics";

    @Autowired private PrismContext prismContext;
    @Autowired @Qualifier("cacheRepositoryService") private RepositoryService repositoryService;
    @Autowired private ModelService modelService;
    @Autowired protected SecurityEnforcer securityEnforcer;
    @Autowired protected AccCertGeneralHelper generalHelper;
    @Autowired protected AccCertEventHelper eventHelper;
    @Autowired protected AccCertQueryHelper queryHelper;
    @Autowired protected AccCertUpdateHelper updateHelper;
    @Autowired protected AccCertCaseOperationsHelper caseHelper;
    @Autowired private AccessCertificationRemediationTaskHandler remediationTaskHandler;

    private Map<String,CertificationHandler> registeredHandlers = new HashMap<>();

    public void registerHandler(String handlerUri, CertificationHandler handler) {
        if (registeredHandlers.containsKey(handlerUri)) {
            throw new IllegalStateException("There is already a handler with URI " + handlerUri);
        }
        registeredHandlers.put(handlerUri, handler);
    }

    public CertificationHandler findCertificationHandler(AccessCertificationCampaignType campaign) {
        if (StringUtils.isBlank(campaign.getHandlerUri())) {
            throw new IllegalArgumentException("No handler URI for access certification campaign " + ObjectTypeUtil.toShortString(campaign));
        }
        CertificationHandler handler = registeredHandlers.get(campaign.getHandlerUri());
        if (handler == null) {
            throw new IllegalStateException("No handler for URI " + campaign.getHandlerUri());
        }
        return handler;
    }

    @Override
    public AccessCertificationCampaignType createCampaign(String definitionOid, Task task, OperationResult parentResult)
            throws SchemaException, SecurityViolationException, ObjectNotFoundException, ObjectAlreadyExistsException {
        Validate.notNull(definitionOid, "definitionOid");
        Validate.notNull(task, "task");
        Validate.notNull(parentResult, "parentResult");

        OperationResult result = parentResult.createSubresult(OPERATION_CREATE_CAMPAIGN);
        try {
            PrismObject<AccessCertificationDefinitionType> definition = repositoryService.getObject(AccessCertificationDefinitionType.class, definitionOid, null, result);
            securityEnforcer.authorize(ModelAuthorizationAction.CREATE_CERTIFICATION_CAMPAIGN.getUrl(), null, definition, null, null, null, result);
            AccessCertificationCampaignType newCampaign = updateHelper.createCampaignObject(definition.asObjectable(), task, result);
            updateHelper.addObject(newCampaign, task, result);
            return newCampaign;
        } catch (RuntimeException e) {
            result.recordFatalError("Couldn't create certification campaign: unexpected exception: " + e.getMessage(), e);
            throw e;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    @Override
    public void openNextStage(String campaignOid, int requestedStageNumber, Task task, OperationResult parentResult) throws SchemaException, SecurityViolationException, ObjectNotFoundException, ObjectAlreadyExistsException {
        Validate.notNull(campaignOid, "campaignOid");
        Validate.notNull(task, "task");
        Validate.notNull(parentResult, "parentResult");

        OperationResult result = parentResult.createSubresult(OPERATION_OPEN_NEXT_STAGE);
        result.addParam("campaignOid", campaignOid);
        result.addParam("requestedStageNumber", requestedStageNumber);

        try {
            AccessCertificationCampaignType campaign = generalHelper.getCampaign(campaignOid, null, task, result);
            result.addParam("campaign", ObjectTypeUtil.toShortString(campaign));

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("openNextStage starting for {}", ObjectTypeUtil.toShortString(campaign));
            }

            securityEnforcer.authorize(ModelAuthorizationAction.OPEN_CERTIFICATION_CAMPAIGN_REVIEW_STAGE.getUrl(), null,
                    campaign.asPrismObject(), null, null, null, result);

            final int currentStageNumber = campaign.getStageNumber();
            final int stages = CertCampaignTypeUtil.getNumberOfStages(campaign);
            final AccessCertificationCampaignStateType state = campaign.getState();
            LOGGER.trace("openNextStage: currentStageNumber={}, stages={}, requestedStageNumber={}, state={}", currentStageNumber, stages, requestedStageNumber, state);
            if (IN_REVIEW_STAGE.equals(state)) {
                result.recordFatalError("Couldn't advance to review stage " + requestedStageNumber + " as the stage " + currentStageNumber + " is currently open.");
            } else if (IN_REMEDIATION.equals(state)) {
                result.recordFatalError("Couldn't advance to review stage " + requestedStageNumber + " as the campaign is currently in the remediation phase.");
            } else if (CLOSED.equals(state)) {
                result.recordFatalError("Couldn't advance to review stage " + requestedStageNumber + " as the campaign is already closed.");
            } else if (!REVIEW_STAGE_DONE.equals(state) && !CREATED.equals(state)) {
                throw new IllegalStateException("Unexpected campaign state: " + state);
            } else if (REVIEW_STAGE_DONE.equals(state) && requestedStageNumber != currentStageNumber+1) {
                result.recordFatalError("Couldn't advance to review stage " + requestedStageNumber + " as the campaign is currently in stage " + currentStageNumber);
            } else if (CREATED.equals(state) && requestedStageNumber != 1) {
                result.recordFatalError("Couldn't advance to review stage " + requestedStageNumber + " as the campaign was just created");
            } else if (requestedStageNumber > stages) {
                result.recordFatalError("Couldn't advance to review stage " + requestedStageNumber + " as the campaign has only " + stages + " stages");
            } else {
                final CertificationHandler handler = findCertificationHandler(campaign);
                final AccessCertificationStageType stage = updateHelper.createStage(campaign, currentStageNumber+1);
                final List<ItemDelta<?,?>> deltas = updateHelper.getDeltasForStageOpen(campaign, stage, handler, task, result);
                updateHelper.modifyObjectViaModel(AccessCertificationCampaignType.class, campaignOid, deltas, task, result);
                updateHelper.afterStageOpen(campaignOid, stage, task, result);
            }
        } catch (RuntimeException e) {
            result.recordFatalError("Couldn't move to certification campaign stage " + requestedStageNumber + ": unexpected exception: " + e.getMessage(), e);
            throw e;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    @Override
    public void closeCurrentStage(String campaignOid, int stageNumberToClose, Task task, OperationResult parentResult) throws SchemaException, SecurityViolationException, ObjectNotFoundException, ObjectAlreadyExistsException {
        Validate.notNull(campaignOid, "campaignOid");
        Validate.notNull(task, "task");
        Validate.notNull(parentResult, "parentResult");

        OperationResult result = parentResult.createSubresult(OPERATION_CLOSE_CURRENT_STAGE);
        result.addParam("campaignOid", campaignOid);
        result.addParam("stageNumber", stageNumberToClose);

        try {
            AccessCertificationCampaignType campaign = generalHelper.getCampaign(campaignOid, null, task, result);
            result.addParam("campaign", ObjectTypeUtil.toShortString(campaign));

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("closeCurrentStage starting for {}", ObjectTypeUtil.toShortString(campaign));
            }

            securityEnforcer.authorize(ModelAuthorizationAction.CLOSE_CERTIFICATION_CAMPAIGN_REVIEW_STAGE.getUrl(), null,
                    campaign.asPrismObject(), null, null, null, result);

            final int currentStageNumber = campaign.getStageNumber();
            final int stages = CertCampaignTypeUtil.getNumberOfStages(campaign);
            final AccessCertificationCampaignStateType state = campaign.getState();
            LOGGER.trace("closeCurrentStage: currentStageNumber={}, stages={}, stageNumberToClose={}, state={}", currentStageNumber, stages, stageNumberToClose, state);

            if (stageNumberToClose != currentStageNumber) {
                result.recordFatalError("Couldn't close review stage " + stageNumberToClose + " as the campaign is not in that stage");
            } else if (!IN_REVIEW_STAGE.equals(state)) {
                result.recordFatalError("Couldn't close review stage " + stageNumberToClose + " as it is currently not open");
            } else {
                List<ItemDelta<?,?>> deltas = updateHelper.getDeltasForStageClose(campaign, result);
                updateHelper.modifyObjectViaModel(AccessCertificationCampaignType.class, campaignOid, deltas, task, result);
                updateHelper.afterStageClose(campaignOid, task, result);
            }
        } catch (RuntimeException e) {
            result.recordFatalError("Couldn't close certification campaign stage " + stageNumberToClose+ ": unexpected exception: " + e.getMessage(), e);
            throw e;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    @Override
    public void startRemediation(String campaignOid, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, SecurityViolationException, ObjectAlreadyExistsException {
        Validate.notNull(campaignOid, "campaignOid");
        Validate.notNull(task, "task");
        Validate.notNull(parentResult, "parentResult");

        OperationResult result = parentResult.createSubresult(OPERATION_CLOSE_CURRENT_STAGE);
        result.addParam("campaignOid", campaignOid);

        try {
            AccessCertificationCampaignType campaign = generalHelper.getCampaign(campaignOid, null, task, result);
            result.addParam("campaign", ObjectTypeUtil.toShortString(campaign));

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("startRemediation starting for {}", ObjectTypeUtil.toShortString(campaign));
            }

            securityEnforcer.authorize(ModelAuthorizationAction.START_CERTIFICATION_REMEDIATION.getUrl(), null,
                    campaign.asPrismObject(), null, null, null, result);

            final int currentStageNumber = campaign.getStageNumber();
            final int lastStageNumber = CertCampaignTypeUtil.getNumberOfStages(campaign);
            final AccessCertificationCampaignStateType state = campaign.getState();
            LOGGER.trace("startRemediation: currentStageNumber={}, stages={}, state={}", currentStageNumber, lastStageNumber, state);

            if (currentStageNumber != lastStageNumber) {
                result.recordFatalError("Couldn't start the remediation as the campaign is not in its last stage ("+lastStageNumber+"); current stage: "+currentStageNumber);
            } else if (!REVIEW_STAGE_DONE.equals(state)) {
                result.recordFatalError("Couldn't start the remediation as the last stage was not properly closed.");
            } else {
                List<ItemDelta<?,?>> deltas = updateHelper.createDeltasForStageNumberAndState(lastStageNumber + 1, IN_REMEDIATION);
                updateHelper.modifyObjectViaModel(AccessCertificationCampaignType.class, campaignOid, deltas, task, result);

                if (CertCampaignTypeUtil.isRemediationAutomatic(campaign)) {
                    remediationTaskHandler.launch(campaign, task, result);
                } else {
                    result.recordWarning("The automated remediation is not configured. The campaign state was set to IN REMEDIATION, but all remediation actions have to be done by hand.");
                }

                campaign = updateHelper.refreshCampaign(campaign, result);
                eventHelper.onCampaignStageStart(campaign, task, result);
            }
        } catch (RuntimeException e) {
            result.recordFatalError("Couldn't start the remediation: unexpected exception: " + e.getMessage(), e);
            throw e;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    @Deprecated
    @Override
    public List<AccessCertificationCaseType> searchDecisionsToReview(ObjectQuery caseQuery, boolean notDecidedOnly,
            Collection<SelectorOptions<GetOperationOptions>> options,
            Task task, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException, SecurityViolationException {
        throw new UnsupportedOperationException("not available any more");
    }

    @Override
    public List<AccessCertificationWorkItemType> searchOpenWorkItems(ObjectQuery baseWorkItemsQuery, boolean notDecidedOnly,
            Collection<SelectorOptions<GetOperationOptions>> options, Task task, OperationResult parentResult)
            throws ObjectNotFoundException, SchemaException, SecurityViolationException {

        OperationResult result = parentResult.createSubresult(OPERATION_SEARCH_OPEN_WORK_ITEMS);

        try {
            securityEnforcer.authorize(ModelAuthorizationAction.READ_OWN_CERTIFICATION_DECISIONS.getUrl(), null,
                    null, null, null, null, result);

            String reviewerOid = securityEnforcer.getPrincipal().getOid();
            return queryHelper.searchWorkItems(baseWorkItemsQuery, reviewerOid, notDecidedOnly, options, task, result);
        } catch (RuntimeException e) {
            result.recordFatalError("Couldn't search for certification work items: unexpected exception: " + e.getMessage(), e);
            throw e;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    @Override
    public void recordDecision(@NotNull String campaignOid, long caseId, long workItemId, @Nullable AccessCertificationResponseType response,
			@Nullable String comment, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException,
			SecurityViolationException, ObjectAlreadyExistsException {

        OperationResult result = parentResult.createSubresult(OPERATION_RECORD_DECISION);
        try {
            securityEnforcer.authorize(ModelAuthorizationAction.RECORD_CERTIFICATION_DECISION.getUrl(), null,
                    null, null, null, null, result);
            caseHelper.recordDecision(campaignOid, caseId, workItemId, response, comment, task, result);
        } catch (RuntimeException e) {
            result.recordFatalError("Couldn't record reviewer decision: unexpected exception: " + e.getMessage(), e);
            throw e;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    public void delegateWorkItems(@NotNull String campaignOid, @NotNull List<AccessCertificationWorkItemType> workItems,
            @NotNull DelegateWorkItemActionType delegateAction, Task task,
            OperationResult parentResult)
			throws SchemaException, SecurityViolationException, ExpressionEvaluationException, ObjectNotFoundException,
			ObjectAlreadyExistsException {
		OperationResult result = parentResult.createSubresult(OPERATION_DELEGATE_WORK_ITEMS);
		result.addParam("campaignOid", campaignOid);
		result.addCollectionOfSerializablesAsParam("workItems", workItems);	// TODO only IDs?
		result.addParam("delegateAction", delegateAction);
		try {
			// TODO security
			securityEnforcer.authorize(ModelAuthorizationAction.DELEGATE_ALL_WORK_ITEMS.getUrl(), null,
					null, null, null, null, result);
			updateHelper.delegateWorkItems(campaignOid, workItems, delegateAction, task, result);
		} catch (RuntimeException|CommonException e) {
			result.recordFatalError("Couldn't delegate work items: unexpected exception: " + e.getMessage(), e);
			throw e;
		} finally {
			result.computeStatusIfUnknown();
		}
    }

    @Override
    public void closeCampaign(String campaignOid, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, SecurityViolationException, ObjectAlreadyExistsException {
        Validate.notNull(campaignOid, "campaignOid");
        Validate.notNull(task, "task");
        Validate.notNull(parentResult, "parentResult");

        OperationResult result = parentResult.createSubresult(OPERATION_CLOSE_CAMPAIGN);

        try {
            AccessCertificationCampaignType campaign = generalHelper.getCampaign(campaignOid, null, task, result);
            securityEnforcer.authorize(ModelAuthorizationAction.CLOSE_CERTIFICATION_CAMPAIGN.getUrl(), null,
                    campaign.asPrismObject(), null, null, null, result);
            updateHelper.closeCampaign(campaign, task, result);
        } catch (RuntimeException e) {
            result.recordFatalError("Couldn't close certification campaign: unexpected exception: " + e.getMessage(), e);
            throw e;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    // this method delegates the authorization to the model
    @Override
    public AccessCertificationCasesStatisticsType getCampaignStatistics(String campaignOid, boolean currentStageOnly, Task task, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, SecurityViolationException, ObjectAlreadyExistsException {
        Validate.notNull(campaignOid, "campaignOid");
        Validate.notNull(task, "task");
        Validate.notNull(parentResult, "parentResult");

        OperationResult result = parentResult.createSubresult(OPERATION_GET_CAMPAIGN_STATISTICS);
        try {
            AccessCertificationCasesStatisticsType stat = new AccessCertificationCasesStatisticsType(prismContext);

            Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions.createCollection(F_CASE, GetOperationOptions.createRetrieve());
            AccessCertificationCampaignType campaign;
            try {
                campaign = modelService.getObject(AccessCertificationCampaignType.class, campaignOid, options, task, parentResult).asObjectable();
            } catch (CommunicationException|ConfigurationException e) {
                throw new SystemException("Unexpected exception while getting campaign object: " + e.getMessage(), e);
            }

            int accept=0, revoke=0, revokeRemedied=0, reduce=0, reduceRemedied=0, delegate=0, noDecision=0, noResponse=0;
            for (AccessCertificationCaseType _case : campaign.getCase()) {
                AccessCertificationResponseType outcome;
                if (currentStageOnly) {
                    if (_case.getStageNumber() == campaign.getStageNumber()) {
                        outcome = OutcomeUtils.fromUri(_case.getCurrentStageOutcome());
                    } else {
                        continue;
                    }
                } else {
                    outcome = OutcomeUtils.fromUri(_case.getOutcome());
                }
                if (outcome == null) {
                    outcome = AccessCertificationResponseType.NO_RESPONSE;
                }
                switch (outcome) {
                    case ACCEPT: accept++; break;
                    case REVOKE: revoke++;
                                 if (_case.getRemediedTimestamp() != null) {
                                     revokeRemedied++;
                                 }
                                 break;
                    case REDUCE: reduce++;
                                 if (_case.getRemediedTimestamp() != null) {
                                    reduceRemedied++;       // currently not possible
                                 }
                                 break;
                    case DELEGATE: delegate++; break;
                    case NOT_DECIDED: noDecision++; break;
                    case NO_RESPONSE: noResponse++; break;
                    default: throw new IllegalStateException("Unexpected outcome: "+outcome);
                }
            }
            stat.setMarkedAsAccept(accept);
            stat.setMarkedAsRevoke(revoke);
            stat.setMarkedAsRevokeAndRemedied(revokeRemedied);
            stat.setMarkedAsReduce(reduce);
            stat.setMarkedAsReduceAndRemedied(reduceRemedied);
            stat.setMarkedAsDelegate(delegate);
            stat.setMarkedAsNotDecide(noDecision);
            stat.setWithoutResponse(noResponse);
            return stat;
        } catch (RuntimeException e) {
            result.recordFatalError("Couldn't get campaign statistics: unexpected exception: " + e.getMessage(), e);
            throw e;
        } finally {
            result.computeStatusIfUnknown();
        }
    }

    @Override
    public void registerCertificationEventListener(AccessCertificationEventListener listener) {
        eventHelper.registerEventListener(listener);
    }
}
