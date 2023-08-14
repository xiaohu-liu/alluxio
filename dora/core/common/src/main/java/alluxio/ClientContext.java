/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio;

import static java.util.Objects.requireNonNull;

import alluxio.annotation.PublicApi;
import alluxio.conf.AlluxioConfiguration;
import alluxio.conf.Configuration;
import alluxio.conf.PropertyKey;
import alluxio.exception.status.AlluxioStatusException;
import alluxio.security.user.UserState;

import java.net.InetSocketAddress;
import javax.security.auth.Subject;

/**
 * A {@link ClientContext} contains information required and pertaining to making network
 * connections and performing operations with remote Alluxio processes. The {@link ClientContext}
 * should only contain the information which is necessary to make those connections.
 *
 * A {@link ClientContext} are not expensive objects to create, however it is important to be
 * aware that if the configuration with which the instance is created does not have the cluster
 * default configuration loaded that any new clients which use the context will need to load the
 * cluster defaults upon connecting to the Alluxio master.
 *
 * Path level configuration may not be needed for any ClientContext, it is currently only used in
 * BaseFileSystem, so it is initially lazily loaded by FileSystemContext when it's needed.
 *
 * Ideally only a single {@link ClientContext} should be needed when initializing an application.
 * This will use as few network resources as possible.
 */
@PublicApi
public class ClientContext {
  private volatile AlluxioConfiguration mClusterConf;
  private volatile String mClusterConfHash;
  private volatile UserState mUserState;
  private volatile boolean mUriValidationEnabled = true;

  /**
   * A client context with information about the subject and configuration of the client.
   *
   * @param subject the security subject to use
   * @param alluxioConf the {@link AlluxioConfiguration} to use
   * @return a new client context with the specified properties and subject
   */
  public static ClientContext create(Subject subject, AlluxioConfiguration alluxioConf) {
    return new ClientContext(subject, alluxioConf);
  }

  /**
   * @param alluxioConf the specified {@link AlluxioConfiguration} to use
   * @return the client context with the given properties and an empty subject
   */
  public static ClientContext create(AlluxioConfiguration alluxioConf) {
    return new ClientContext(new Subject(), alluxioConf);
  }

  /**
   * @return a new {@link ClientContext} with values loaded from the alluxio-site properties and
   * an empty subject.
   */
  public static ClientContext create() {
    return new ClientContext(new Subject(), Configuration.global());
  }

  /**
   * This constructor does not create a copy of the configuration.
   */
  protected ClientContext(ClientContext ctx) {
    mClusterConf = ctx.getClusterConf();
    mUserState = ctx.getUserState();
    mClusterConfHash = ctx.getClusterConfHash();
    mUriValidationEnabled = ctx.getUriValidationEnabled();
  }

  protected ClientContext(Subject subject, AlluxioConfiguration alluxioConf) {
    requireNonNull(subject, "subject is null");
    mClusterConf = requireNonNull(alluxioConf, "alluxioConf is null");
    mClusterConfHash = alluxioConf.hash();
    mUserState = UserState.Factory.create(mClusterConf, subject);
  }

  /**
   * This method will load the cluster and path level configuration defaults and update
   * the configuration in one RPC.
   *
   * This method should be synchronized so that concurrent calls to it don't continually overwrite
   * the previous configuration.
   *
   * The cluster defaults are updated per connection establishment, or when cluster defaults
   * updates are detected on client side.
   *
   * @param address the address to load cluster defaults from
   * @param loadClusterConf whether to load cluster level configuration
   * @throws AlluxioStatusException
   */
  public synchronized void loadConf(InetSocketAddress address, boolean loadClusterConf)
      throws AlluxioStatusException {
    // TODO(yyong) so far remove it, will check if it is required to change for improvement.
    return;
  }

  /**
   * Loads configuration if not loaded from meta master yet.
   *
   * @param address meta master address
   * @throws AlluxioStatusException
   */
  public synchronized void loadConfIfNotLoaded(InetSocketAddress address)
      throws AlluxioStatusException {
    if (!mClusterConf.getBoolean(PropertyKey.USER_CONF_CLUSTER_DEFAULT_ENABLED)) {
      return;
    }
    mUserState = UserState.Factory.create(mClusterConf, mUserState.getSubject());
  }

  /**
   * @param uriValidationEnabled whether URI validation is enabled
   * @return updated instance of ClientContext
   */
  public ClientContext setUriValidationEnabled(boolean uriValidationEnabled) {
    mUriValidationEnabled = uriValidationEnabled;
    return this;
  }

  /**
   * @return {@code true} if URI validation is enabled
   */
  public boolean getUriValidationEnabled() {
    return mUriValidationEnabled;
  }

  /**
   * @return the cluster level configuration backing this context
   */
  public AlluxioConfiguration getClusterConf() {
    return mClusterConf;
  }

  protected void setClusterConf(AlluxioConfiguration alluxioConfiguration) {
    mClusterConf = alluxioConfiguration;
  }

  protected void setClusterConfHash(String clusterConfHash) {
    mClusterConfHash = clusterConfHash;
  }

  /**
   * @return hash of cluster level configuration
   */
  public String getClusterConfHash() {
    return mClusterConfHash;
  }

  /**
   * @return the Subject backing this context
   */
  public Subject getSubject() {
    return mUserState.getSubject();
  }

  /**
   * @return the UserState for this context
   */
  public UserState getUserState() {
    return mUserState;
  }
}
