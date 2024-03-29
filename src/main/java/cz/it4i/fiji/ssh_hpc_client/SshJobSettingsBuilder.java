
package cz.it4i.fiji.ssh_hpc_client;

import java.io.Serializable;

import cz.it4i.cluster_job_launcher.SshJobSettings;

public class SshJobSettingsBuilder implements Serializable {

	private static final int DEFAULT_NUMBER_OF_NODES = 1;
	private static final int DEFAULT_NUMBER_OF_CORES_PER_NODE = 24;

	private int numberOfNodes = DEFAULT_NUMBER_OF_NODES;
	private int numberOfCoresPerNode = DEFAULT_NUMBER_OF_CORES_PER_NODE;
	private int maxMemoryPerNode;

	private String queueOrPartition;
	private String userScriptName;
	private int[] walltime = new int[2];
	
	private boolean scatter;

	public SshJobSettingsBuilder numberOfCoresPerNode(
		int newNumberOfCoresPerNode)
	{
		this.numberOfCoresPerNode = newNumberOfCoresPerNode;
		return this;
	}

	public SshJobSettingsBuilder queueOrPartition(String newQueueOrPartition) {
		this.queueOrPartition = newQueueOrPartition;
		return this;
	}

	public SshJobSettingsBuilder walltime(int[] newWalltime) {
		this.walltime = newWalltime;
		return this;
	}

	public SshJobSettingsBuilder userScriptName(String newUserScriptName) {
		this.userScriptName = newUserScriptName;
		return this;
	}

	public SshJobSettingsBuilder maxMemoryPerNode(int newMaxMemoryPerNode) {
		this.maxMemoryPerNode = newMaxMemoryPerNode;
		return this;
	}

	public SshJobSettingsBuilder numberOfNodes(int newNumberOfNodes) {
		this.numberOfNodes = newNumberOfNodes;
		return this;
	}
	
	public SshJobSettingsBuilder scatter(boolean newScatter) {
		this.scatter = newScatter;
		return this;
	}

	public SshJobSettings build() {
		return new SshJobSettings() {

			@Override
			public int getNumberOfNodes() {
				return numberOfNodes;
			}

			@Override
			public int getNumberOfCoresPerNode() {
				return numberOfCoresPerNode;
			}

			@Override
			public String getQueueOrPartition() {
				return queueOrPartition;
			}

			@Override
			public int[] getWalltime() {
				return walltime;
			}

			@Override
			public String getUserScriptName() {
				return userScriptName;
			}

			@Override
			public int getMaxMemoryPerNode() {
				return maxMemoryPerNode;
			}
			
			@Override
			public boolean getScatter() {
				return scatter;
			}
		};
	}
}
