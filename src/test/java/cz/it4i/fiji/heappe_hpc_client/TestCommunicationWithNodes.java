
package cz.it4i.fiji.heappe_hpc_client;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.it4i.fiji.hpc_client.HPCClient;
import cz.it4i.fiji.hpc_client.HPCFileTransfer;
import cz.it4i.fiji.hpc_client.JobInfo;
import cz.it4i.fiji.hpc_client.JobState;
import cz.it4i.fiji.hpc_client.TunnelToNode;

public class TestCommunicationWithNodes {

	public static Logger log = LoggerFactory.getLogger(
		cz.it4i.fiji.heappe_hpc_client.TestCommunicationWithNodes.class);

	private static String[] predefined = new String[2];

	@SuppressWarnings("resource")
	public static void main(String[] args) throws IOException,
		InterruptedException
	{
		predefined[0] =
			"POST /modules/'command:net.imagej.ops.math.PrimitiveMath$IntegerAdd'?process=false HTTP/1.1\r\n" +
				"Content-Type: application/json\r\n" + "Host: localhost:8080\r\n" +
				"Content-Length: 13\r\n" + "\r\n" + "{\"a\":1,\"b\":3}";

		predefined[1] = //
			"GET /modules HTTP/1.1\r\n" + //
				"Host: localhost:8080\r\n" + //
				"User-Agent: curl/7.47.0\r\n" + //
				"Accept: */*\r\n" + //
				"\r\n";

		HaaSClientSettings settings = SettingsProvider.getSettings("DD-18-42",
			TestingConstants.CONFIGURATION_FILE_NAME);
		HaaSClient client = new HaaSClient(settings);
		long id = startBDS(client);
		String sessionID = client.getSessionID();
		log.info(id + " - " + client.obtainJobInfo(id).getState() + " - " +
			sessionID);
		if (client.obtainJobInfo(id).getState() != JobState.Running && client
			.obtainJobInfo(id).getState() != JobState.Queued)
		{
			client.submitJob(id);
		}

		while (client.obtainJobInfo(id).getState() == JobState.Queued) {
			log.info("" + client.obtainJobInfo(id).getState());
			Thread.sleep(5000);
		}
		String ip;
		log.info("adresess " + (ip = client.obtainJobInfo(id).getNodesIPs().get(
			0)));
		try (TunnelToNode tunnel = client.openTunnel(id, ip, 8081, 8081)) {
			log.info(tunnel.getLocalHost() + ":" + tunnel.getLocalPort());
			System.out.println("Press enter");
			new Scanner(System.in).nextLine();
		}
	}

	public static long startBDS(HPCClient<?> client) throws InterruptedException {
		long jobId =
			439; /*client.createJob(new
						JobSettingsBuilder().jobName("TestOutRedirect").templateId(4l)
						.walltimeLimit(3600).clusterNodeType(7l).build(), Collections.emptyList());*/

		JobInfo info = client.obtainJobInfo(jobId);
		log.info("JobId :" + jobId + ", state - " + info.getState());
		if (info.getState() != JobState.Running && info
			.getState() != JobState.Queued)
		{
			HPCFileTransfer transfer = client.startFileTransfer(jobId);
			try {
				transfer.upload(new UploadingFileData("run-bds"));
			}
			catch (InterruptedIOException e) {
				log.error(e.getMessage(), e);
			}
			client.submitJob(jobId);
		}
		JobState state;
		while ((state = client.obtainJobInfo(jobId)
			.getState()) != JobState.Running)
		{
			log.info("state - " + state);
			Thread.sleep(3000);
		}
		return jobId;
	}

}
