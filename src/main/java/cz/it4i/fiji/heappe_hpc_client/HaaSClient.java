
package cz.it4i.fiji.heappe_hpc_client;

import com.jcraft.jsch.JSchException;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.rpc.ServiceException;
import javax.xml.ws.WebServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.it4i.fiji.haas_java_client.proxy.ArrayOfCommandTemplateParameterValueExt;
import cz.it4i.fiji.haas_java_client.proxy.ArrayOfEnvironmentVariableExt;
import cz.it4i.fiji.haas_java_client.proxy.ArrayOfTaskFileOffsetExt;
import cz.it4i.fiji.haas_java_client.proxy.ArrayOfTaskSpecificationExt;
import cz.it4i.fiji.haas_java_client.proxy.CommandTemplateParameterValueExt;
import cz.it4i.fiji.haas_java_client.proxy.DataTransferMethodExt;
import cz.it4i.fiji.haas_java_client.proxy.DataTransferWs;
import cz.it4i.fiji.haas_java_client.proxy.DataTransferWsSoap;
import cz.it4i.fiji.haas_java_client.proxy.FileTransferMethodExt;
import cz.it4i.fiji.haas_java_client.proxy.FileTransferWs;
import cz.it4i.fiji.haas_java_client.proxy.FileTransferWsSoap;
import cz.it4i.fiji.haas_java_client.proxy.JobManagementWs;
import cz.it4i.fiji.haas_java_client.proxy.JobManagementWsSoap;
import cz.it4i.fiji.haas_java_client.proxy.JobPriorityExt;
import cz.it4i.fiji.haas_java_client.proxy.JobSpecificationExt;
import cz.it4i.fiji.haas_java_client.proxy.JobStateExt;
import cz.it4i.fiji.haas_java_client.proxy.PasswordCredentialsExt;
import cz.it4i.fiji.haas_java_client.proxy.SubmittedJobInfoExt;
import cz.it4i.fiji.haas_java_client.proxy.SubmittedTaskInfoExt;
import cz.it4i.fiji.haas_java_client.proxy.TaskFileOffsetExt;
import cz.it4i.fiji.haas_java_client.proxy.TaskSpecificationExt;
import cz.it4i.fiji.haas_java_client.proxy.UserAndLimitationManagementWs;
import cz.it4i.fiji.haas_java_client.proxy.UserAndLimitationManagementWsSoap;
import cz.it4i.fiji.hpc_client.AuthenticationException;
import cz.it4i.fiji.hpc_client.HPCClient;
import cz.it4i.fiji.hpc_client.HPCDataTransfer;
import cz.it4i.fiji.hpc_client.HPCFileTransfer;
import cz.it4i.fiji.hpc_client.JobFileContent;
import cz.it4i.fiji.hpc_client.JobInfo;
import cz.it4i.fiji.hpc_client.JobState;
import cz.it4i.fiji.hpc_client.NotConnectedException;
import cz.it4i.fiji.hpc_client.ProgressNotifier;
import cz.it4i.fiji.hpc_client.SynchronizableFile;
import cz.it4i.fiji.hpc_client.TunnelToNode;
import cz.it4i.fiji.scpclient.ScpClient;
import cz.it4i.fiji.scpclient.TransferFileProgress;

public class HaaSClient implements HPCClient<JobSettings> {

	private static Logger log = LoggerFactory.getLogger(
		cz.it4i.fiji.heappe_hpc_client.HaaSClient.class);

	private static final Map<JobStateExt, JobState> WS_STATE2STATE;

	static {
		final Map<JobStateExt, JobState> map = new EnumMap<>(JobStateExt.class);
		map.put(JobStateExt.CANCELED, JobState.Canceled);
		map.put(JobStateExt.CONFIGURING, JobState.Configuring);
		map.put(JobStateExt.FAILED, JobState.Failed);
		map.put(JobStateExt.FINISHED, JobState.Finished);
		map.put(JobStateExt.QUEUED, JobState.Queued);
		map.put(JobStateExt.RUNNING, JobState.Running);
		map.put(JobStateExt.SUBMITTED, JobState.Submitted);
		WS_STATE2STATE = Collections.unmodifiableMap(map);
	}

	private String sessionID;

	private UserAndLimitationManagementWsSoap userAndLimitationManagement;

	private JobManagementWsSoap jobManagement;

	private FileTransferWsSoap fileTransferWS;

	private final String projectId;

	private final Map<Long, FileTransferPool> filetransferPoolMap =
		new HashMap<>();

	private final HaaSClientSettings settings;

	private DataTransferWsSoap dataTransferWs;

	public HaaSClient(final HaaSClientSettings settings) {
		this.settings = settings;
		this.projectId = settings.getProjectId();
	}

	@Override
	public void checkConnection() {
		getSessionID();
	}

	@Override
	public long createJob(final JobSettings jobSettings) {
		final Collection<TaskSpecificationExt> taskSpec = Arrays.asList(
			createTaskSpecification(jobSettings));
		final JobSpecificationExt jobSpecification = createJobSpecification(
			jobSettings, taskSpec);
		final SubmittedJobInfoExt job = getJobManagement().createJob(
			jobSpecification, getSessionID());
		return job.getId();
	}

	@Override
	public HPCFileTransfer startFileTransfer(final long jobId,
		final TransferFileProgress notifier)
	{
		return createFileTransfer(jobId, notifier);
	}

	@Override
	public void submitJob(final long jobId) {
		getJobManagement().submitJob(jobId, getSessionID());
	}

	@Override
	public JobInfo obtainJobInfo(final long jobId) {
		final SubmittedJobInfoExt info = getJobManagement().getCurrentInfoForJob(
			jobId, getSessionID());

		final Collection<Long> tasksId = info.getTasks().getSubmittedTaskInfoExt()
			.stream().map(SubmittedTaskInfoExt::getId).collect(Collectors.toList());
		return new JobInfo() {

			private List<String> ips;

			@Override
			public Collection<Long> getTasks() {
				return tasksId;
			}

			@Override
			public JobState getState() {
				return WS_STATE2STATE.get(info.getState());
			}

			@Override
			public java.util.Calendar getStartTime() {
				return toGregorian(info.getStartTime());
			}

			@Override
			public java.util.Calendar getEndTime() {
				return toGregorian(info.getEndTime());
			}

			@Override
			public Calendar getCreationTime() {
				return toGregorian(info.getCreationTime());
			}

			@Override
			public List<String> getNodesIPs() {
				if (ips == null) {
					ips = getJobManagement().getAllocatedNodesIPs(jobId, getSessionID())
						.getString().stream().collect(Collectors.toList());

				}
				return ips;
			}
		};
	}

	@Override
	public List<JobFileContent> downloadPartsOfJobFiles(final Long jobId,
		final List<SynchronizableFile> files)
	{
		final ArrayOfTaskFileOffsetExt fileOffsetExt =
			new ArrayOfTaskFileOffsetExt();
		fileOffsetExt.getTaskFileOffsetExt().addAll(constructTaskFileOffsetExts(
			files));
		return getFileTransfer().downloadPartsOfJobFilesFromCluster(jobId,
			fileOffsetExt, getSessionID()).getJobFileContentExt().stream().map(
				JobFileContentToExt::new).collect(Collectors.toList());
	}

	@Override
	public Collection<String> getChangedFiles(final long jobId) {
		return getFileTransfer().listChangedFilesForJob(jobId, getSessionID())
			.getString();
	}

	@Override
	public void cancelJob(final Long jobId) {
		getJobManagement().cancelJob(jobId, getSessionID());
	}

	@Override
	public void deleteJob(final long id) {
		getJobManagement().deleteJob(id, getSessionID());
	}

	@Override
	public HPCDataTransfer startDataTransfer(final long jobId,
		final int nodeNumber, final int port)
	{
		return createDataTransfer(jobId, nodeNumber, port);
	}

	public TunnelToNode openTunnel(final long jobId, final String nodeIP,
		final int localPort, final int remotePort)
	{
		MiddlewareTunnel tunnel;
		try {
			tunnel = new MiddlewareTunnel(Executors.newCachedThreadPool(), jobId,
				nodeIP, getSessionID());
			tunnel.open(localPort, remotePort);
			return new TunnelToNode() {

				@Override
				public void close() throws IOException {
					tunnel.close();
				}

				@Override
				public int getLocalPort() {
					return tunnel.getLocalPort();
				}

				@Override
				public String getLocalHost() {
					return tunnel.getLocalHost();
				}
			};
		}
		catch (final IOException e) {
			throw new HaaSClientException(e);
		}
	}

	synchronized String getSessionID() {
		if (sessionID == null) {
			sessionID = authenticate();
		}
		return sessionID;
	}

	private HPCFileTransfer createFileTransfer(final long jobId,
		final TransferFileProgress progress)
	{
		final FileTransferPool pool = filetransferPoolMap.computeIfAbsent(jobId,
			this::createFileTransferPool);

		return new HaasFileTransferReconnectingAfterAuthFail(
			getHaasFileTransferFactory(pool, progress), pool::reconnect);
	}

	private FileTransferPool createFileTransferPool(final long jobId) {
		return new CountingFileTransferPool(new DelayingFileTransferPool(
			new PFileTransferPool(jobId)));
	}

	private java.util.function.Supplier<HPCFileTransfer>
		getHaasFileTransferFactory(FileTransferPool pool,
			TransferFileProgress progress)
	{
		return () -> {

			final FileTransferMethodExt ft = pool.obtain();
			try {
				return new HaaSFileTransferImp(ft, getScpClient(ft), progress);
			}
			catch (JSchException e) {
				pool.release();
				throw new HaaSClientException(e);
			}
		};
	}

	private HPCDataTransfer createDataTransfer(final long jobId,
		final int nodeNumber, final int port)
	{
		final String host = getJobManagement().getAllocatedNodesIPs(jobId,
			getSessionID()).getString().get(nodeNumber);
		final DataTransferWsSoap ws = getDataTransfer();
		final DataTransferMethodExt dataTransferMethodExt = ws
			.getDataTransferMethod(host, port, jobId, getSessionID());
		final String sessionId = getSessionID();
		return new HPCDataTransfer() {

			@Override
			public void close() throws IOException {
				if (log.isDebugEnabled()) {
					log.debug("close");
				}
				ws.endDataTransfer(dataTransferMethodExt, sessionId);
				if (log.isDebugEnabled()) {
					log.debug("close - DONE");
				}
			}

			@Override
			public void write(final byte[] buffer) {
				if (log.isDebugEnabled()) {
					log.debug("write: {}", new String(buffer));
				}
				ws.writeDataToJobNode(buffer, jobId, host, sessionId, false);
				if (log.isDebugEnabled()) {
					log.debug("write - DONE");
				}
			}

			@Override
			public byte[] read() {
				if (log.isDebugEnabled()) {
					log.debug("read: ");
				}
				final byte[] result = ws.readDataFromJobNode(jobId, host, sessionId);
				if (log.isDebugEnabled()) {
					log.debug("read - DONE: \"{}\"", result != null ? new String(result)
						: "EOF");
				}
				return result;
			}

			@Override
			public void closeConnection() {
				if (log.isDebugEnabled()) {
					log.debug("closeConnection");
				}
				ws.writeDataToJobNode(null, jobId, host, sessionId, true);
				if (log.isDebugEnabled()) {
					log.debug("closeConnection - DONE");
				}
			}
		};
	}

	private ScpClient getScpClient(final FileTransferMethodExt fileTransfer)
		throws JSchException
	{
		final byte[] pvtKey = fileTransfer.getCredentials().getPrivateKey()
			.getBytes(StandardCharsets.UTF_8);
		return new ScpClient(fileTransfer.getServerHostname(), fileTransfer
			.getCredentials().getUsername(), pvtKey);
	}

	private JobSpecificationExt createJobSpecification(
		final JobSettings jobSettings, final Collection<TaskSpecificationExt> tasks)
	{
		final JobSpecificationExt testJob = new JobSpecificationExt();
		testJob.setName(jobSettings.getJobName());
		testJob.setMinCores(jobSettings.getNumberOfCoresPerNode() * jobSettings
			.getNumberOfNodes());
		testJob.setMaxCores(jobSettings.getNumberOfCoresPerNode() * jobSettings
			.getNumberOfNodes());
		testJob.setPriority(JobPriorityExt.AVERAGE);
		testJob.setProject(projectId);
		testJob.setWaitingLimit(null);
		testJob.setWalltimeLimit(jobSettings.getWalltimeLimit());
		testJob.setNotificationEmail(settings.getEmail());
		testJob.setPhoneNumber(settings.getPhone());
		testJob.setNotifyOnAbort(false);
		testJob.setNotifyOnFinish(false);
		testJob.setNotifyOnStart(false);
		testJob.setClusterNodeTypeId(jobSettings.getClusterNodeType());
		testJob.setEnvironmentVariables(new ArrayOfEnvironmentVariableExt());
		testJob.setTasks(getAndFill(new ArrayOfTaskSpecificationExt(), a -> a
			.getTaskSpecificationExt().addAll(tasks)));
		return testJob;
	}

	private TaskSpecificationExt createTaskSpecification(
		final JobSettings jobSettings)
	{

		final TaskSpecificationExt testTask = new TaskSpecificationExt();
		testTask.setName(jobSettings.getJobName() + "-task");
		testTask.setMinCores(jobSettings.getNumberOfCoresPerNode() * jobSettings
			.getNumberOfNodes());
		testTask.setMaxCores(jobSettings.getNumberOfCoresPerNode() * jobSettings
			.getNumberOfNodes());
		testTask.setWalltimeLimit(jobSettings.getWalltimeLimit());
		testTask.setRequiredNodes(null);
		testTask.setIsExclusive(false);
		testTask.setIsRerunnable(false);
		testTask.setStandardInputFile(null);
		testTask.setStandardOutputFile("console_Stdout");
		testTask.setStandardErrorFile("console_Stderr");
		testTask.setProgressFile("console_Stdprog");
		testTask.setLogFile("console_Stdlog");
		testTask.setClusterTaskSubdirectory(null);
		testTask.setCommandTemplateId(jobSettings.getTemplateId());
		testTask.setEnvironmentVariables(new ArrayOfEnvironmentVariableExt());
		testTask.setDependsOn(null);
		testTask.setTemplateParameterValues(getAndFill(
			new ArrayOfCommandTemplateParameterValueExt(), t -> t
				.getCommandTemplateParameterValueExt().addAll(jobSettings
					.getTemplateParameters().stream().map(
						pair -> createCommandTemplateParameterValueExt(pair.getKey(), pair
							.getValue())).collect(Collectors.toList()))));
		return testTask;
	}

	private String authenticate() {
		try {
			return getUserAndLimitationManagement().authenticateUserPassword(
				createPasswordCredentialsExt(settings.getUserName(), settings
					.getPassword()));
		}
		catch (WebServiceException e) {
			if (e.getMessage().contains(
				"The server sent HTTP status code 500: Internal Server Error"))
			{
				throw new AuthenticationException(e);
			}
			tryNotConnected(e);
			throw e;
		}
	}

	private void tryNotConnected(RuntimeException e) {
		if (e.getMessage().contains("HTTP transport error") && (e
			.getCause() instanceof UnknownHostException || (e
				.getCause() instanceof SocketException && e.getCause().getMessage()
					.contains("Network is unreachable (connect failed)"))))
		{
			throw new NotConnectedException(e);
		}
	}

	private synchronized DataTransferWsSoap getDataTransfer() {
		if (dataTransferWs == null) {
			dataTransferWs = new DataTransferWs().getDataTransferWsSoap12();
		}
		return dataTransferWs;
	}

	private synchronized UserAndLimitationManagementWsSoap
		getUserAndLimitationManagement()
	{
		if (userAndLimitationManagement == null) {
			userAndLimitationManagement = new UserAndLimitationManagementWs()
				.getUserAndLimitationManagementWsSoap12();
		}
		return userAndLimitationManagement;
	}

	private synchronized JobManagementWsSoap getJobManagement() {
		if (jobManagement == null) {
			jobManagement = new JobManagementWs().getJobManagementWsSoap12();
		}
		return jobManagement;
	}

	private synchronized FileTransferWsSoap getFileTransfer() {
		if (fileTransferWS == null) {
			fileTransferWS = new FileTransferWs().getFileTransferWsSoap12();
		}
		return fileTransferWS;
	}

	public static class PProgressNotifierDecorator4Size extends
		PProgressNotifierDecorator
	{

		private static final int SIZE_RATIO = 20;

		public PProgressNotifierDecorator4Size(final ProgressNotifier notifier) {
			super(notifier);

		}

		@Override
		public void setItemCount(final int count, final int total) {
			super.setItemCount(count, total);
			setCount(count, total * SIZE_RATIO);
		}
	}

	public static class PProgressNotifierDecorator implements ProgressNotifier {

		private final ProgressNotifier notifier;

		public PProgressNotifierDecorator(final ProgressNotifier notifier) {
			this.notifier = notifier;
		}

		@Override
		public void setTitle(final String title) {
			notifier.setTitle(title);
		}

		@Override
		public void setCount(final int count, final int total) {
			notifier.setCount(count, total);
		}

		@Override
		public void addItem(final Object item) {
			notifier.addItem(item);
		}

		@Override
		public void setItemCount(final int count, final int total) {
			notifier.setItemCount(count, total);
		}

		@Override
		public void itemDone(final Object item) {
			notifier.itemDone(item);
		}

		@Override
		public void done() {
			notifier.done();
		}
	}

	private interface PSupplier<T> {

		T get() throws RemoteException, ServiceException;
	}

	private interface PConsumer<T> {

		void accept(T val) throws RemoteException, ServiceException;
	}

	private class PFileTransferPool implements FileTransferPool {

		private FileTransferMethodExt holded;

		private final PSupplier<FileTransferMethodExt> factory;
		private final PConsumer<FileTransferMethodExt> destroyer;

		public PFileTransferPool(final long jobId) {
			this.factory = () -> getFileTransfer().getFileTransferMethod(jobId,
				getSessionID());
			this.destroyer = val -> getFileTransfer().endFileTransfer(jobId, val,
				getSessionID());
		}

		@Override
		public synchronized void reconnect() {
			try {
				if (holded != null) {
					destroyer.accept(holded);
				}
				sessionID = null;
				if (holded != null) {
					holded = factory.get();
				}
			}
			catch (RemoteException | ServiceException exc) {
				throw new HaaSClientException(exc);
			}
		}

		@Override
		public synchronized FileTransferMethodExt obtain() {
			if (holded == null) {
				try {
					holded = factory.get();
				}
				catch (RemoteException | ServiceException exc) {
					throw new HaaSClientException(exc);
				}
			}
			return holded;
		}

		@Override
		public synchronized void release() {
			try {
				destroyer.accept(holded);
			}
			catch (RemoteException | ServiceException exc) {
				throw new HaaSClientException(exc);
			}
			holded = null;
		}
	}

	private static TaskFileOffsetExt constructTaskFileOffsetExt(
		SynchronizableFile file)
	{
		TaskFileOffsetExt result = new TaskFileOffsetExt();
		result.setSubmittedTaskInfoId(file.getTaskId());
		result.setFileType(JobFileContentToExt.getExt(file.getType()));
		result.setOffset(file.getOffset());
		return result;
	}

	private static Collection<? extends TaskFileOffsetExt>
		constructTaskFileOffsetExts(List<SynchronizableFile> files)
	{
		return files.stream().map(HaaSClient::constructTaskFileOffsetExt).collect(
			Collectors.toList());
	}

	private static <T> T getAndFill(final T value, final Consumer<T> filler) {
		filler.accept(value);
		return value;
	}

	private static Calendar toGregorian(final XMLGregorianCalendar time) {
		return Optional.ofNullable(time).map(
			XMLGregorianCalendar::toGregorianCalendar).orElse(null);
	}

	private static CommandTemplateParameterValueExt
		createCommandTemplateParameterValueExt(final String key, final String value)
	{
		final CommandTemplateParameterValueExt result =
			new CommandTemplateParameterValueExt();
		result.setCommandParameterIdentifier(key);
		result.setParameterValue(value);
		return result;
	}

	private static PasswordCredentialsExt createPasswordCredentialsExt(
		final String userName, final String password)
	{
		final PasswordCredentialsExt result = new PasswordCredentialsExt();
		result.setUsername(userName);
		result.setPassword(password);
		return result;
	}

	@Override
	public void close() {
		// ToDo: Make ScpClient a property and close it here.
		// Only one ScpClient should exit as the number of connections are limited.
	}

	@Override
	public String getRemoteJobInfo(long jobId) {
		return "This job is managed by the HEAppE middleware.";
	}

	@Override
	public void getRemotePreviewCommand(long id) {
		// This job is managed by the HEAppE middleware.;
	}

}
