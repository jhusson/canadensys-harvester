package net.canadensys.harvester.occurrence.controller;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import net.canadensys.dataportal.occurrence.model.ImportLogModel;
import net.canadensys.harvester.AbstractProcessingJob;
import net.canadensys.harvester.ItemProgressListenerIF;
import net.canadensys.harvester.config.harvester.HarvesterConfigIF;
import net.canadensys.harvester.occurrence.SharedParameterEnum;
import net.canadensys.harvester.occurrence.dao.IPTFeedDAO;
import net.canadensys.harvester.occurrence.job.ComputeUniqueValueJob;
import net.canadensys.harvester.occurrence.job.ImportDwcaJob;
import net.canadensys.harvester.occurrence.job.MoveToPublicSchemaJob;
import net.canadensys.harvester.occurrence.model.IPTFeedModel;
import net.canadensys.harvester.occurrence.model.JobStatusModel;
import net.canadensys.harvester.occurrence.model.ResourceModel;
import net.canadensys.harvester.occurrence.notification.ResourceStatusNotifierIF;
import net.canadensys.harvester.occurrence.view.model.HarvesterViewModel;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Main controller to initiate jobs.
 * This controller is NOT thread safe.
 * @author canadensys
 *
 */
@Component("stepController")
public class StepController implements StepControllerIF {

	@Autowired
	private HarvesterConfigIF harvesterConfig;

	@Autowired
	@Qualifier(value="publicSessionFactory")
	private SessionFactory sessionFactory;
	
	@Autowired
	private IPTFeedDAO iptFeedDAO;
	
	@Autowired
	private ResourceStatusNotifierIF notifier;

	@Autowired
	private ImportDwcaJob importDwcaJob;

	@Autowired
	private MoveToPublicSchemaJob moveToPublicSchemaJob;

	@Autowired
	private ComputeUniqueValueJob computeUniqueValueJob;

	@Autowired
	private HarvesterViewModel harvesterViewModel;

	@Autowired
	private NodeStatusController nodeStatusController;

	private AbstractProcessingJob currentJob;

	public StepController(){}

	@Override
	public void registerProgressListener(ItemProgressListenerIF progressListener){
		importDwcaJob.setItemProgressListener(progressListener);
	}

	@Override
	public void importDwcA(Integer resourceId){
		//enable node status controller
		nodeStatusController.start();
		importDwcaJob.addToSharedParameters(SharedParameterEnum.RESOURCE_ID, resourceId);
		currentJob = importDwcaJob;

		JobStatusModel jobStatusModel = new JobStatusModel();
		harvesterViewModel.encapsulateJobStatus(jobStatusModel);
		importDwcaJob.doJob(jobStatusModel);
	}

	@Override
	public void importDwcAFromLocalFile(String dwcaFilePath){
		//enable node status controller
		nodeStatusController.start();
		importDwcaJob.addToSharedParameters(SharedParameterEnum.DWCA_PATH, dwcaFilePath);
		currentJob = importDwcaJob;

		JobStatusModel jobStatusModel = new JobStatusModel();
		harvesterViewModel.encapsulateJobStatus(jobStatusModel);
		importDwcaJob.doJob(jobStatusModel);
	}

	@Override
	public void moveToPublicSchema(String datasetShortName){
		moveToPublicSchemaJob.addToSharedParameters(SharedParameterEnum.DATASET_SHORTNAME, datasetShortName);
		JobStatusModel jobStatusModel = new JobStatusModel();
		harvesterViewModel.encapsulateJobStatus(jobStatusModel);
		
		moveToPublicSchemaJob.doJob(jobStatusModel);
		currentJob = moveToPublicSchemaJob;

		computeUniqueValueJob.doJob(jobStatusModel);
		currentJob = computeUniqueValueJob;
	}


	@Override
	@SuppressWarnings("unchecked")
	@Transactional("publicTransactionManager")
	public List<ResourceModel> getResourceModelList(){
		Criteria searchCriteria = sessionFactory.getCurrentSession().createCriteria(ResourceModel.class);
		return searchCriteria.list();
	}

	@Transactional("publicTransactionManager")
	@Override
	public boolean updateResourceModel(ResourceModel resourceModel) {
		try{
			sessionFactory.getCurrentSession().saveOrUpdate(resourceModel);
		}
		catch(HibernateException hEx){
			hEx.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Get the sorted ImportLogModel list using our own session. Sorted by desc
	 * event_date
	 * 
	 * @return
	 */
	@Override
	@SuppressWarnings("unchecked")
	@Transactional("publicTransactionManager")
	public List<ImportLogModel> getSortedImportLogModelList() {
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(ImportLogModel.class);
		criteria.addOrder(Order.desc("event_end_date_time"));
		return criteria.list();
	}

	@Override
	public void onNodeError(){
		//stop the current job
		currentJob.cancel();
	}
	
	@Override
	@Transactional("publicTransactionManager")
	public List<IPTFeedModel> getIPTFeed() {
		
		URL mainIPTUrl = null;
		try {
			mainIPTUrl = new URL(harvesterConfig.getIptRssAddress());
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
		return iptFeedDAO.getIPTFeed(mainIPTUrl);
	}
	
	@Override
	public List<ResourceModel> getResourceToHarvest() {
		return notifier.getHarvestRequiredList();
	}
}
