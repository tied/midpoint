package com.evolveum.midpoint.provisioning.consistency.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.provisioning.api.GenericConnectorException;
import com.evolveum.midpoint.provisioning.api.ResourceOperationDescription;
import com.evolveum.midpoint.provisioning.consistency.api.ErrorHandler;
import com.evolveum.midpoint.provisioning.impl.ResourceTypeManager;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.util.ShadowCacheUtil;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.DeltaConvertor;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceObjectShadowUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.xml.ns._public.common.api_types_2.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AvailabilityStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.FailedOperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.OperationalStateType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;
import com.evolveum.prism.xml.ns._public.types_2.ItemDeltaType;
import com.evolveum.prism.xml.ns._public.types_2.ModificationTypeType;
import com.evolveum.prism.xml.ns._public.types_2.ObjectDeltaType;

@Component
public class CommunicationExceptionHandler extends ErrorHandler {

	@Autowired
	@Qualifier("cacheRepositoryService")
	private RepositoryService cacheRepositoryService;

	public CommunicationExceptionHandler() {
		cacheRepositoryService = null;
	}

	private static final Trace LOGGER = TraceManager.getTrace(CommunicationExceptionHandler.class);
	/**
	 * Get the value of repositoryService.
	 * 
	 * @return the value of repositoryService
	 */
	public RepositoryService getCacheRepositoryService() {
		return cacheRepositoryService;
	}

	/**
	 * Set the value of repositoryService
	 * 
	 * Expected to be injected.
	 * 
	 * @param repositoryService
	 *            new value of repositoryService
	 */
	public void setCacheRepositoryService(RepositoryService repositoryService) {
		this.cacheRepositoryService = repositoryService;
	}

	@Override
	public <T extends ResourceObjectShadowType> T handleError(T shadow, FailedOperation op, Exception ex, boolean compensate, 
			Task task, OperationResult parentResult) throws SchemaException, GenericFrameworkException, CommunicationException,
			ObjectNotFoundException, ObjectAlreadyExistsException, ConfigurationException {

		Validate.notNull(shadow, "Shadow must not be null.");
		
		OperationResult operationResult = parentResult.createSubresult("Compensation for communication problem. Operation: " + op.name());
		operationResult.addParam("shadow", shadow);
		operationResult.addParam("currentOperation", op);
		operationResult.addParam("exception", ex.getMessage());

		// first modify last availability status in the resource, so by others
		// operations, we can know that it is down
		modifyResourceAvailabilityStatus(shadow.getResource(), AvailabilityStatusType.DOWN, operationResult);
		
		if (!isPostpone(shadow.getResource()) || !compensate){
			LOGGER.trace("Postponing operation turned off.");
			throw new CommunicationException(ex.getMessage(), ex);
		}
		
//		Task task = null; 
		ObjectDelta delta = null;
		ResourceOperationDescription operationDescription = null;
		switch (op) {
		case ADD:
			// if it is firt time, just store the whole account to the repo
			LOGGER.trace("Postponing ADD operation for shadow {}", ObjectTypeUtil.toShortString(shadow));
			ResourceType resource = shadow.getResource();
			if (shadow.getFailedOperationType() == null) {
//				ResourceType resource = shadow.getResource();
				if (shadow.getName() == null) {
					shadow.setName(ShadowCacheUtil.determineShadowName(shadow.asPrismObject()));
				}
				if (shadow.getResourceRef() == null || shadow.getResourceRef().getOid() == null){
					if (resource != null){
					shadow.getResourceRef().setOid(shadow.getResource().getOid());
					}
				}
				
				if (shadow.getResourceRef() != null && resource != null){
					shadow.setResource(null);
				}
				shadow.setAttemptNumber(getAttemptNumber(shadow));
				shadow.setFailedOperationType(FailedOperationTypeType.ADD);
				String oid = cacheRepositoryService.addObject(shadow.asPrismObject(), null, operationResult);
				shadow.setOid(oid);
			
				// if it is seccond time ,just increade the attempt number
			} else {
				if (FailedOperationTypeType.ADD == shadow.getFailedOperationType()) {

					Collection<? extends ItemDelta> attemptdelta = createAttemptModification(shadow, null);
					cacheRepositoryService.modifyObject(AccountShadowType.class, shadow.getOid(), attemptdelta,
							operationResult);
			
				}

			}
			// if the shadow was successfully stored in the repo, just mute the
			// error
			for (OperationResult subRes : parentResult.getSubresults()) {
				subRes.muteError();
			}
			operationResult.computeStatus();
			parentResult
					.recordHandledError("Could not create account=" +shadow.getName().getOrig()+" on the resource, because "
									+ ObjectTypeUtil.toShortString(resource)
									+ " is unreachable at the moment. Shadow is stored in the repository and the account will be created when the resource goes online");   // there will be something like ": Add object failed" appended, so the final dot was a bit ugly here
			
//			task = taskManager.createTaskInstance();
			delta = ObjectDelta.createAddDelta(shadow.asPrismObject());
			operationDescription = createOperationDescription(shadow, resource, delta, task, operationResult);
			changeNotificationDispatcher.notifyInProgress(operationDescription, task, parentResult);
			return shadow;
		case MODIFY:
			if (shadow.getFailedOperationType() == null || shadow.getFailedOperationType() == FailedOperationTypeType.MODIFY) {

				shadow.setFailedOperationType(FailedOperationTypeType.MODIFY);
				Collection<ItemDelta> modifications = createShadowModification(shadow);

				getCacheRepositoryService().modifyObject(AccountShadowType.class, shadow.getOid(), modifications,
						operationResult);
				delta = ObjectDelta.createModifyDelta(shadow.getOid(), modifications, shadow.asPrismObject().getCompileTimeClass(), prismContext);
//				operationResult.recordSuccess();
				// return shadow;
			} else {
				if (FailedOperationTypeType.ADD == shadow.getFailedOperationType()) {
					if (shadow.getObjectChange() != null && shadow.getOid() != null) {
						Collection<? extends ItemDelta> deltas = DeltaConvertor.toModifications(shadow
								.getObjectChange().getModification(), shadow.asPrismObject().getDefinition());

						cacheRepositoryService.modifyObject(AccountShadowType.class, shadow.getOid(), deltas,
								operationResult);
						delta = ObjectDelta.createModifyDelta(shadow.getOid(), deltas, shadow.asPrismObject().getCompileTimeClass(), prismContext);
						// return shadow;
//						operationResult.recordSuccess();
					}
				}
			}
			for (OperationResult subRes : parentResult.getSubresults()) {
				subRes.muteError();
			}
			operationResult.computeStatus();
			parentResult
					.recordHandledError("Could not apply modifications to "+ObjectTypeUtil.toShortString(shadow)+" on the "
									+ ObjectTypeUtil.toShortString(shadow.getResource())
									+ ", because resource is unreachable. Modifications will be applied when the resource goes online");
//			task = taskManager.createTaskInstance();
//			
			operationDescription = createOperationDescription(shadow, shadow.getResource(), delta, task, operationResult);
			changeNotificationDispatcher.notifyInProgress(operationDescription, task, parentResult);
			return shadow;
		case DELETE:
			shadow.setFailedOperationType(FailedOperationTypeType.DELETE);
			Collection<ItemDelta> modifications = createShadowModification(shadow);

			getCacheRepositoryService().modifyObject(AccountShadowType.class, shadow.getOid(), modifications,
					operationResult);
			for (OperationResult subRes : parentResult.getSubresults()) {
				subRes.muteError();
			}
			parentResult
					.recordHandledError("Could not delete " +ObjectTypeUtil.getShortTypeName(shadow)+ " from the resource "
									+ ObjectTypeUtil.toShortString(shadow.getResource())
									+ ", because resource is unreachable. Account will be delete when the resource goes online");
//			operationResult.recordSuccess();
			operationResult.computeStatus();
//			task = taskManager.createTaskInstance();
//			task.setChannel(QNameUtil.qNameToUri(SchemaConstants.CHANGE_CHANNEL_DISCOVERY));
			delta = ObjectDelta.createDeleteDelta(shadow.asPrismObject().getCompileTimeClass(), shadow.getOid(), prismContext);
			operationDescription = createOperationDescription(shadow, shadow.getResource(), delta, task, operationResult);
			changeNotificationDispatcher.notifyInProgress(operationDescription, task, parentResult);
			return shadow;
		case GET:
			// nothing to do, just return the shadow from the repo and set fetch
			// result..
			for (OperationResult subRes : parentResult.getSubresults()) {
				subRes.muteError();
			}
			operationResult.recordPartialError("Could not get "+ObjectTypeUtil.toShortString(shadow)+" from the resource "
					+ ObjectTypeUtil.toShortString(shadow.getResource())
					+ ", because resource is unreachable. Returning shadow from the repository");
			shadow.setFetchResult(operationResult.createOperationResultType());
//			operationResult.recordSuccess();
//			operationResult.computeStatus();
			return shadow;
		default:
			throw new CommunicationException(ex);
		}

	}

	private void modifyResourceAvailabilityStatus(ResourceType resource, AvailabilityStatusType status, OperationResult result) throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {
				
		if (resource.getOperationalState() == null || resource.getOperationalState().getLastAvailabilityStatus() == null || resource.getOperationalState().getLastAvailabilityStatus() != status) {
			List<PropertyDelta> modifications = new ArrayList<PropertyDelta>();
			PropertyDelta statusDelta = PropertyDelta.createModificationReplaceProperty(OperationalStateType.F_LAST_AVAILABILITY_STATUS, resource.asPrismObject().getDefinition(), status);
			modifications.add(statusDelta);
			statusDelta.setParentPath(new ItemPath(ResourceType.F_OPERATIONAL_STATE));
			cacheRepositoryService.modifyObject(ResourceType.class, resource.getOid(), modifications, result);
		}
		if (resource.getOperationalState() == null){
			OperationalStateType operationalState = new OperationalStateType();
			operationalState.setLastAvailabilityStatus(status);
			resource.setOperationalState(operationalState);
		} else{
			resource.getOperationalState().setLastAvailabilityStatus(status);
		}
	}
	
	private <T extends ResourceObjectShadowType> Collection<ItemDelta> createShadowModification(T shadow) throws ObjectNotFoundException, SchemaException {
		Collection<ItemDelta> modifications = new ArrayList<ItemDelta>();

		PropertyDelta propertyDelta = PropertyDelta.createReplaceDelta(shadow.asPrismObject()
				.getDefinition(), ResourceObjectShadowType.F_RESULT, shadow.getResult());
		modifications.add(propertyDelta);

		propertyDelta = PropertyDelta.createReplaceDelta(shadow.asPrismObject().getDefinition(),
				ResourceObjectShadowType.F_FAILED_OPERATION_TYPE, shadow.getFailedOperationType());
		modifications.add(propertyDelta);
		if (shadow.getObjectChange() != null) {
			propertyDelta = PropertyDelta.createReplaceDelta(shadow.asPrismObject().getDefinition(),
					ResourceObjectShadowType.F_OBJECT_CHANGE, shadow.getObjectChange());
			modifications.add(propertyDelta);
		}
	
		modifications = createAttemptModification(shadow, modifications);
		
		return modifications;
	}
	
	
//	private Integer getAttemptNumber(ResourceObjectShadowType shadow) {
//		Integer attemptNumber = (shadow.getAttemptNumber() == null ? 0 : shadow.getAttemptNumber()+1);
//		return attemptNumber;
//	}
}
