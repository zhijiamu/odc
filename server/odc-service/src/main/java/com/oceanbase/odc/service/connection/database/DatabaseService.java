/*
 * Copyright (c) 2023 OceanBase.
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
package com.oceanbase.odc.service.connection.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import javax.sql.DataSource;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.oceanbase.odc.core.authority.SecurityManager;
import com.oceanbase.odc.core.authority.permission.Permission;
import com.oceanbase.odc.core.authority.util.Authenticated;
import com.oceanbase.odc.core.authority.util.PreAuthenticate;
import com.oceanbase.odc.core.authority.util.SkipAuthorize;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.shared.PreConditions;
import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.constant.OrganizationType;
import com.oceanbase.odc.core.shared.constant.ResourceRoleName;
import com.oceanbase.odc.core.shared.constant.ResourceType;
import com.oceanbase.odc.core.shared.exception.AccessDeniedException;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.core.shared.exception.ConflictException;
import com.oceanbase.odc.core.shared.exception.NotFoundException;
import com.oceanbase.odc.metadb.connection.DatabaseEntity;
import com.oceanbase.odc.metadb.connection.DatabaseRepository;
import com.oceanbase.odc.metadb.connection.DatabaseSpecs;
import com.oceanbase.odc.metadb.dbobject.DBColumnRepository;
import com.oceanbase.odc.metadb.dbobject.DBObjectRepository;
import com.oceanbase.odc.metadb.iam.PermissionRepository;
import com.oceanbase.odc.metadb.iam.UserDatabasePermissionEntity;
import com.oceanbase.odc.metadb.iam.UserDatabasePermissionRepository;
import com.oceanbase.odc.metadb.iam.UserPermissionRepository;
import com.oceanbase.odc.metadb.iam.UserTablePermissionEntity;
import com.oceanbase.odc.metadb.iam.UserTablePermissionRepository;
import com.oceanbase.odc.service.collaboration.environment.EnvironmentService;
import com.oceanbase.odc.service.collaboration.environment.model.Environment;
import com.oceanbase.odc.service.collaboration.project.ProjectService;
import com.oceanbase.odc.service.collaboration.project.model.Project;
import com.oceanbase.odc.service.collaboration.project.model.QueryProjectParams;
import com.oceanbase.odc.service.common.model.InnerUser;
import com.oceanbase.odc.service.connection.ConnectionService;
import com.oceanbase.odc.service.connection.database.model.CreateDatabaseReq;
import com.oceanbase.odc.service.connection.database.model.Database;
import com.oceanbase.odc.service.connection.database.model.DatabaseSyncStatus;
import com.oceanbase.odc.service.connection.database.model.DatabaseType;
import com.oceanbase.odc.service.connection.database.model.DatabaseUser;
import com.oceanbase.odc.service.connection.database.model.DeleteDatabasesReq;
import com.oceanbase.odc.service.connection.database.model.ModifyDatabaseOwnerReq;
import com.oceanbase.odc.service.connection.database.model.QueryDatabaseParams;
import com.oceanbase.odc.service.connection.database.model.TransferDatabasesReq;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;
import com.oceanbase.odc.service.db.DBSchemaService;
import com.oceanbase.odc.service.db.schema.DBSchemaSyncTaskManager;
import com.oceanbase.odc.service.db.schema.GlobalSearchProperties;
import com.oceanbase.odc.service.db.schema.model.DBObjectSyncStatus;
import com.oceanbase.odc.service.db.schema.syncer.DBSchemaSyncProperties;
import com.oceanbase.odc.service.iam.HorizontalDataPermissionValidator;
import com.oceanbase.odc.service.iam.OrganizationService;
import com.oceanbase.odc.service.iam.ProjectPermissionValidator;
import com.oceanbase.odc.service.iam.ResourceRoleService;
import com.oceanbase.odc.service.iam.UserService;
import com.oceanbase.odc.service.iam.auth.AuthenticationFacade;
import com.oceanbase.odc.service.iam.model.User;
import com.oceanbase.odc.service.iam.model.UserResourceRole;
import com.oceanbase.odc.service.onlineschemachange.ddl.DBUser;
import com.oceanbase.odc.service.onlineschemachange.ddl.OscDBAccessor;
import com.oceanbase.odc.service.onlineschemachange.ddl.OscDBAccessorFactory;
import com.oceanbase.odc.service.onlineschemachange.rename.OscDBUserUtil;
import com.oceanbase.odc.service.permission.DBResourcePermissionHelper;
import com.oceanbase.odc.service.permission.database.model.DatabasePermissionType;
import com.oceanbase.odc.service.plugin.SchemaPluginUtil;
import com.oceanbase.odc.service.session.factory.DefaultConnectSessionFactory;
import com.oceanbase.odc.service.session.factory.OBConsoleDataSourceFactory;
import com.oceanbase.odc.service.session.model.SqlExecuteResult;
import com.oceanbase.odc.service.task.runtime.PreCheckTaskParameters.AuthorizedDatabase;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author: Lebie
 * @Date: 2023/6/5 14:34
 * @Description: []
 */
@Service
@Slf4j
@Validated
@Authenticated
public class DatabaseService {

    private final DatabaseMapper databaseMapper = DatabaseMapper.INSTANCE;

    @Autowired
    private DatabaseRepository databaseRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectPermissionValidator projectPermissionValidator;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private AuthenticationFacade authenticationFacade;

    @Autowired
    private DBSchemaService dbSchemaService;

    @Autowired
    private JdbcLockRegistry jdbcLockRegistry;

    @Autowired
    private HorizontalDataPermissionValidator horizontalDataPermissionValidator;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private UserDatabasePermissionRepository userDatabasePermissionRepository;

    @Autowired
    private UserTablePermissionRepository userTablePermissionRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserPermissionRepository userPermissionRepository;

    @Autowired
    private DBObjectRepository dbObjectRepository;

    @Autowired
    private DBColumnRepository dbColumnRepository;

    @Autowired
    private DBResourcePermissionHelper permissionHelper;

    @Autowired
    private ResourceRoleService resourceRoleService;

    @Autowired
    private UserService userService;

    @Autowired
    private SecurityManager securityManager;

    @Autowired
    private DBSchemaSyncTaskManager dbSchemaSyncTaskManager;

    @Autowired
    private DBSchemaSyncProperties dbSchemaSyncProperties;

    @Autowired
    private GlobalSearchProperties globalSearchProperties;

    /**
     * 根据数据库ID获取数据库详情
     *
     * @param id 数据库ID
     * @return 数据库详情
     * @throws NotFoundException 如果找不到对应的数据库则抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal authenticated")
    public Database detail(@NonNull Long id) {
        // 通过数据库ID获取数据库信息
        Database database = entityToModel(databaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_DATABASE, "id", id)), true);
        // 检查当前用户是否有该数据库的水平权限
        horizontalDataPermissionValidator.checkCurrentOrganization(database);
        // 如果数据库所属项目不为空且项目ID不为空，则检查当前用户是否有该项目的权限
        if (Objects.nonNull(database.getProject()) && Objects.nonNull(database.getProject().getId())) {
            projectPermissionValidator.checkProjectRole(database.getProject().getId(), ResourceRoleName.all());
            return database;
        }
        // 否则，检查当前用户是否有读取该数据源的权限
        Permission requiredPermission = this.securityManager
                .getPermissionByActions(database.getDataSource(), Collections.singletonList("read"));
        if (this.securityManager.isPermitted(requiredPermission)) {
            return database;
        }
        // 如果当前用户没有足够的权限，则抛出异常
        throw new NotFoundException(ResourceType.ODC_DATABASE, "id", id);
    }

    @SkipAuthorize("odc internal usage")
    public Database getBasicSkipPermissionCheck(Long id) {
        return databaseMapper.entityToModel(databaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_DATABASE, "id", id)));
    }

    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public ConnectionConfig findDataSourceForConnectById(@NonNull Long id) {
        DatabaseEntity database = databaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_DATABASE, "id", id));
        return connectionService.getForConnectionSkipPermissionCheck(database.getConnectionId());
    }

    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public ConnectionConfig findDataSourceForTaskById(@NonNull Long id) {
        DatabaseEntity database = databaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResourceType.ODC_DATABASE, "id", id));
        return connectionService.getDecryptedConfig(database.getConnectionId());
    }

    /**
     * 根据数据源ID和数据库名称列表分页查询数据库列表
     *
     * @param id 数据源ID
     * @param name 数据库名称
     * @param pageable 分页信息
     * @return 符合条件的数据库列表
     */
    @PreAuthenticate(actions = "read", resourceType = "ODC_CONNECTION", indexOfIdParam = 0)
    public Page<Database> listDatabasesByDataSource(@NonNull Long id, String name, @NonNull Pageable pageable) {
        // 构建数据库实体的查询规范
        Specification<DatabaseEntity> specs = DatabaseSpecs
                .connectionIdEquals(id)
                .and(DatabaseSpecs.nameLike(name));
        // 根据查询规范和分页信息查询数据库实体列表
        Page<DatabaseEntity> entities = databaseRepository.findAll(specs, pageable);
        // 将数据库实体列表转换为数据库模型列表
        Page<Database> databases = entitiesToModels(entities, false);
        // 检查当前组织是否有权限访问这些数据库
        horizontalDataPermissionValidator.checkCurrentOrganization(databases.getContent());
        return databases;
    }

    /**
     * 列出数据库列表
     *
     * @param params   查询数据库参数
     * @param pageable 分页信息
     * @return 数据库列表
     */
    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal authenticated")
    public Page<Database> list(@NonNull QueryDatabaseParams params, @NotNull Pageable pageable) {
        // 如果数据源ID不为空且当前用户所属组织类型为个人，则同步数据源模式
        if (Objects.nonNull(params.getDataSourceId())
            && authenticationFacade.currentUser().getOrganizationType() == OrganizationType.INDIVIDUAL) {
            try {
                internalSyncDataSourceSchemas(params.getDataSourceId());
            } catch (Exception ex) {
                log.warn("sync data sources in individual space failed when listing databases, errorMessage={}",
                    ex.getLocalizedMessage());
            }
            params.setContainsUnassigned(true);
        }
        // 构建数据库实体的规约条件
        Specification<DatabaseEntity> specs = DatabaseSpecs
            .environmentIdEquals(params.getEnvironmentId())
            .and(DatabaseSpecs.nameLike(params.getSchemaName()))
            .and(DatabaseSpecs.typeIn(params.getTypes()))
            .and(DatabaseSpecs.existedEquals(params.getExisted()))
            .and(DatabaseSpecs.organizationIdEquals(authenticationFacade.currentOrganizationId()));
        // 获取当前用户加入的项目ID集合
        Set<Long> joinedProjectIds =
            projectService
                .list(QueryProjectParams.builder().build(), Pageable.unpaged())
                .getContent().stream()
                .filter(Objects::nonNull).map(Project::getId).collect(Collectors.toSet());
        /**
         * not joined any projects and does not show unassigned databases
         */
        if (joinedProjectIds.isEmpty()
            && (Objects.isNull(params.getContainsUnassigned()) || !params.getContainsUnassigned())) {
            return Page.empty();
        }

        // 如果查询参数中未指定项目ID，则根据当前用户加入的项目ID集合构建项目规约条件
        if (Objects.isNull(params.getProjectId())) {
            Specification<DatabaseEntity> projectSpecs = DatabaseSpecs.projectIdIn(joinedProjectIds);
            if (Objects.nonNull(params.getContainsUnassigned()) && params.getContainsUnassigned()) {
                projectSpecs = projectSpecs.or(DatabaseSpecs.projectIdIsNull());
            }
            specs = specs.and(projectSpecs);
        } else {
            // 如果查询参数中指定了项目ID，则判断该项目ID是否属于当前用户加入的项目ID集合
            if (!joinedProjectIds.contains(params.getProjectId())) {
                throw new AccessDeniedException();
            }
            specs = specs.and(DatabaseSpecs.projectIdEquals(params.getProjectId()));
        }

        // 如果查询参数中指定了数据源ID，则构建连接ID规约条件
        if (Objects.nonNull(params.getDataSourceId())) {
            specs = specs.and(DatabaseSpecs.connectionIdEquals(params.getDataSourceId()));
        }
        // 根据规约条件和分页信息获取数据库实体列表，并转换为数据库模型列表返回
        Page<DatabaseEntity> entities = databaseRepository.findAll(specs, pageable);
        return entitiesToModels(entities,
            Objects.nonNull(params.getIncludesPermittedAction()) && params.getIncludesPermittedAction());
    }

    @SkipAuthorize("internal authenticated")
    public List<ConnectionConfig> statsConnectionConfig() {
        QueryDatabaseParams params = QueryDatabaseParams.builder().build();
        if (authenticationFacade.currentUser().getOrganizationType() == OrganizationType.INDIVIDUAL) {
            return connectionService.listByOrganizationId(authenticationFacade.currentOrganizationId());
        }
        Page<Database> databases = list(params, Pageable.unpaged());
        if (CollectionUtils.isEmpty(databases.getContent())) {
            return Collections.emptyList();
        }
        return databases.stream().filter(database -> Objects.nonNull(database.getDataSource()))
                .map(Database::getDataSource)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(ConnectionConfig::getId))),
                        ArrayList::new));
    }

    /**
     * 业务库创建好数据库，并在odc元数据库中同步新增的connect_database记录
     *
     * @param req 创建数据库请求
     * @return 创建成功的数据库实体
     */
    @SkipAuthorize("internal authenticated")
    public Database create(@NonNull CreateDatabaseReq req) {
        // 获取连接配置
        ConnectionConfig connection = connectionService.getForConnectionSkipPermissionCheck(req.getDataSourceId());
        // 判断连接配置的项目ID是否与请求的项目ID相同，或者请求的项目ID是否为空，或者当前用户是否有该项目的Owner或DBA角色，或者当前用户是否有该数据源的update权限
        if ((connection.getProjectId() != null && !connection.getProjectId().equals(req.getProjectId()))
            || (Objects.nonNull(req.getProjectId())
                && !projectPermissionValidator.hasProjectRole(req.getProjectId(),
            Arrays.asList(ResourceRoleName.OWNER, ResourceRoleName.DBA)))
            || !connectionService.checkPermission(req.getDataSourceId(), Collections.singletonList("update"))) {
            throw new AccessDeniedException();
        }
        // 获取数据源
        DataSource dataSource = new OBConsoleDataSourceFactory(connection, true, false).getDataSource();
        try (Connection conn = dataSource.getConnection()) {
            // 创建数据库
            createDatabase(req, conn, connection);
            // 获取数据库详情
            DBDatabase dbDatabase = dbSchemaService.detail(connection.getDialectType(), conn, req.getName());
            // 构建数据库实体
            DatabaseEntity database = new DatabaseEntity();
            database.setDatabaseId(dbDatabase.getId());
            database.setExisted(Boolean.TRUE);
            database.setName(dbDatabase.getName());
            database.setCharsetName(dbDatabase.getCharset());
            database.setCollationName(dbDatabase.getCollation());
            database.setConnectionId(req.getDataSourceId());
            database.setProjectId(req.getProjectId());
            database.setEnvironmentId(connection.getEnvironmentId());
            database.setSyncStatus(DatabaseSyncStatus.SUCCEEDED);
            database.setOrganizationId(authenticationFacade.currentOrganizationId());
            database.setLastSyncTime(new Date(System.currentTimeMillis()));
            database.setObjectSyncStatus(DBObjectSyncStatus.INITIALIZED);
            database.setDialectType(connection.getDialectType());
            database.setType(DatabaseType.PHYSICAL);
            // 保存数据库实体到元数据中并返回
            DatabaseEntity saved = databaseRepository.saveAndFlush(database);
            List<UserResourceRole> userResourceRoles = buildUserResourceRoles(Collections.singleton(saved.getId()),
                req.getOwnerIds());
            resourceRoleService.saveAll(userResourceRoles);
            return entityToModel(saved, false);
        } catch (Exception ex) {
            throw new BadRequestException(SqlExecuteResult.getTrackMessage(ex));
        } finally {
            // 关闭数据源
            if (dataSource instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) dataSource).close();
                } catch (Exception e) {
                    log.warn("Failed to close datasource", e);
                }
            }
        }
    }

    @SkipAuthorize("internal usage")
    public Set<Long> listDatabaseIdsByProjectId(@NonNull Long projectId) {
        return databaseRepository.findByProjectId(projectId).stream().map(DatabaseEntity::getId)
                .collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public Set<Long> listExistDatabaseIdsByProjectId(@NonNull Long projectId) {
        return databaseRepository.findByProjectIdAndExisted(projectId, true).stream().map(DatabaseEntity::getId)
                .collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public Set<Long> listDatabaseIdsByConnectionIds(@NotEmpty Collection<Long> connectionIds) {
        return databaseRepository.findByConnectionIdIn(connectionIds).stream().map(DatabaseEntity::getId)
                .collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public List<Database> listDatabasesByIds(@NotEmpty Collection<Long> ids) {
        return databaseRepository.findByIdIn(ids).stream().map(databaseMapper::entityToModel)
                .collect(Collectors.toList());
    }

    @SkipAuthorize("internal usage")
    public List<Database> listDatabasesDetailsByIds(@NotEmpty Collection<Long> ids) {
        Specification<DatabaseEntity> specs = DatabaseSpecs.idIn(ids);
        return entitiesToModels(databaseRepository.findAll(specs, Pageable.unpaged()), true).getContent();
    }

    @SkipAuthorize("internal usage")
    public List<Database> listDatabasesByConnectionIds(@NotEmpty Collection<Long> connectionIds) {
        return databaseRepository.findByConnectionIdIn(connectionIds).stream().map(databaseMapper::entityToModel)
                .collect(Collectors.toList());
    }

    @SkipAuthorize("internal usage")
    public List<Database> listExistDatabasesByConnectionId(@NotNull Long connectionId) {
        return databaseRepository.findByConnectionIdAndExisted(connectionId, true).stream()
                .map(databaseMapper::entityToModel).collect(Collectors.toList());
    }

    @SkipAuthorize("internal usage")
    public Set<Database> listExistDatabasesByProjectId(@NonNull Long projectId) {
        return databaseRepository.findByProjectIdAndExisted(projectId, true).stream()
                .map(databaseMapper::entityToModel).collect(Collectors.toSet());
    }

    @SkipAuthorize("internal usage")
    public Set<Database> listDatabaseByNames(@NotEmpty Collection<String> names) {
        return databaseRepository.findByNameIn(names).stream().map(databaseMapper::entityToModel)
                .collect(Collectors.toSet());
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal authenticated")
    public boolean transfer(@NonNull @Valid TransferDatabasesReq req) {
        List<DatabaseEntity> entities = databaseRepository.findAllById(req.getDatabaseIds());
        if (CollectionUtils.isEmpty(entities)) {
            return false;
        }
        checkTransferable(entities, req);
        Set<Long> databaseIds = entities.stream().map(DatabaseEntity::getId).collect(Collectors.toSet());
        databaseRepository.setProjectIdByIdIn(req.getProjectId(), databaseIds);
        deleteDatabaseRelatedPermissionByIds(databaseIds);
        List<UserResourceRole> userResourceRoles = buildUserResourceRoles(databaseIds, req.getOwnerIds());
        resourceRoleService.saveAll(userResourceRoles);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal authenticated")
    public boolean deleteDatabases(@NonNull DeleteDatabasesReq req) {
        if (CollectionUtils.isEmpty(req.getDatabaseIds())) {
            return true;
        }
        List<DatabaseEntity> saved = databaseRepository.findByIdIn(req.getDatabaseIds()).stream()
                .filter(database -> !database.getExisted())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(saved)) {
            return false;
        }
        saved.forEach(database -> checkPermission(database.getProjectId(), database.getConnectionId()));
        Set<Long> databaseIds = saved.stream().map(DatabaseEntity::getId).collect(Collectors.toSet());
        deleteDatabaseRelatedPermissionByIds(databaseIds);
        dbColumnRepository.deleteByDatabaseIdIn(req.getDatabaseIds());
        dbObjectRepository.deleteByDatabaseIdIn(req.getDatabaseIds());
        databaseRepository.deleteAll(saved);
        return true;
    }

    /**
     * 同步数据源模式
     *
     * @param dataSourceId 数据源ID
     * @return 是否同步成功
     * @throws InterruptedException 线程中断异常
     */
    @Transactional(rollbackFor = Exception.class)
    @PreAuthenticate(actions = "update", resourceType = "ODC_CONNECTION", indexOfIdParam = 0)
    public Boolean syncDataSourceSchemas(@NonNull Long dataSourceId) throws InterruptedException {
        // 调用内部方法同步数据源中的对象
        Boolean res = internalSyncDataSourceSchemas(dataSourceId);
        try {
            // 刷新过期的待处理数据库对象状态
            refreshExpiredPendingDBObjectStatus();
            // 根据数据源提交数据库模式同步任务
            dbSchemaSyncTaskManager
                .submitTaskByDataSource(connectionService.getBasicWithoutPermissionCheck(dataSourceId));
        } catch (Exception e) {
            // 记录日志
            log.warn("Failed to submit sync database schema task for datasource id={}", dataSourceId, e);
        }
        return res;
    }

    /**
     * 内部同步数据源模式
     *
     * @param dataSourceId 数据源ID
     * @return 同步结果
     * @throws InterruptedException 线程中断异常
     */
    @SkipAuthorize("internal usage")
    public Boolean internalSyncDataSourceSchemas(@NonNull Long dataSourceId) throws InterruptedException {
        // 获取jdbc锁
        Lock lock = jdbcLockRegistry.obtain(connectionService.getUpdateDsSchemaLockKey(dataSourceId));
        if (!lock.tryLock(3, TimeUnit.SECONDS)) {
            // 如果无法获取锁，则抛出冲突异常
            throw new ConflictException(ErrorCodes.ResourceSynchronizing,
                    new Object[] {ResourceType.ODC_DATABASE.getLocalizedMessage()}, "Can not acquire jdbc lock");
        }
        try {
            // 获取数据库连接配置
            ConnectionConfig connection = connectionService.getForConnectionSkipPermissionCheck(dataSourceId);
            // 检查当前组织是否有权限
            horizontalDataPermissionValidator.checkCurrentOrganization(connection);
            // 根据组织类型同步数据源
            organizationService.get(connection.getOrganizationId()).ifPresent(organization -> {
                if (organization.getType() == OrganizationType.INDIVIDUAL) {
                    syncIndividualDataSources(connection);
                } else {
                    syncTeamDataSources(connection);
                }
            });
            return true;
        } catch (Exception ex) {
            // 同步失败，记录日志并返回false
            log.warn("Sync database failed, dataSourceId={}, errorMessage={}", dataSourceId, ex.getLocalizedMessage());
            return false;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    private void syncTeamDataSources(ConnectionConfig connection) {
        // 获取当前项目ID
        Long currentProjectId = connection.getProjectId();
        // 判断是否阻止同步到项目时排除架构
        boolean blockExcludeSchemas = dbSchemaSyncProperties.isBlockExclusionsWhenSyncDbToProject();
        // 获取排除的架构列表
        List<String> excludeSchemas = dbSchemaSyncProperties.getExcludeSchemas(connection.getDialectType());
        // 创建团队数据源
        DataSource teamDataSource = new OBConsoleDataSourceFactory(connection, true, false).getDataSource();
        // 创建线程池
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        // 提交任务
        Future<List<DatabaseEntity>> future = executorService.submit(() -> {
            try (Connection conn = teamDataSource.getConnection()) {
                // 获取数据库实体列表
                return dbSchemaService.listDatabases(connection.getDialectType(), conn).stream().map(database -> {
                    DatabaseEntity entity = new DatabaseEntity();
                    // 生成唯一标识符
                    entity.setDatabaseId(com.oceanbase.odc.common.util.StringUtils.uuid());
                    entity.setExisted(Boolean.TRUE);
                    entity.setName(database.getName());
                    entity.setCharsetName(database.getCharset());
                    entity.setCollationName(database.getCollation());
                    entity.setTableCount(0L);
                    entity.setOrganizationId(connection.getOrganizationId());
                    entity.setEnvironmentId(connection.getEnvironmentId());
                    entity.setConnectionId(connection.getId());
                    entity.setSyncStatus(DatabaseSyncStatus.SUCCEEDED);
                    entity.setProjectId(currentProjectId);
                    entity.setObjectSyncStatus(DBObjectSyncStatus.INITIALIZED);
                    // 如果阻止同步到项目并且架构在排除列表中，则不设置项目ID
                    if (blockExcludeSchemas && excludeSchemas.contains(database.getName())) {
                        entity.setProjectId(null);
                    }
                    return entity;
                }).collect(Collectors.toList());
            }
        });
        try {
            // 获取最新的数据库实体列表
            List<DatabaseEntity> latestDatabases = future.get(10, TimeUnit.SECONDS);
            // 将最新的数据库实体列表按名称分组
            Map<String, List<DatabaseEntity>> latestDatabaseName2Database =
                latestDatabases.stream().filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(DatabaseEntity::getName));
            List<DatabaseEntity> existedDatabasesInDb =
                databaseRepository.findByConnectionId(connection.getId()).stream()
                    .filter(DatabaseEntity::getExisted).collect(Collectors.toList());
            // 将已存在的数据库实体列表按名称分组
            Map<String, List<DatabaseEntity>> existedDatabaseName2Database =
                existedDatabasesInDb.stream().collect(Collectors.groupingBy(DatabaseEntity::getName));

            // 获取已存在的数据库名称列表和最新的数据库名称列表
            Set<String> existedDatabaseNames = existedDatabaseName2Database.keySet();
            Set<String> latestDatabaseNames = latestDatabaseName2Database.keySet();
            // 计算需要添加、删除和更新的数据库实体列表
            List<Object[]> toAdd = latestDatabases.stream()
                .filter(database -> !existedDatabaseNames.contains(database.getName()))
                .map(database -> new Object[] {
                    database.getDatabaseId(),
                    database.getOrganizationId(),
                    database.getName(),
                    database.getProjectId(),
                    database.getConnectionId(),
                    database.getEnvironmentId(),
                    database.getSyncStatus().name(),
                    database.getCharsetName(),
                    database.getCollationName(),
                    database.getTableCount(),
                    database.getExisted(),
                    database.getObjectSyncStatus().name()
                }).collect(Collectors.toList());

            // 批量插入数据库实体
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            if (CollectionUtils.isNotEmpty(toAdd)) {
                jdbcTemplate.batchUpdate(
                    "insert into connect_database(database_id, organization_id, name, project_id, connection_id, "
                    + "environment_id, sync_status, charset_name, collation_name, table_count, is_existed, "
                    + "object_sync_status) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                    toAdd);
            }
            List<Object[]> toDelete = existedDatabasesInDb.stream()
                .filter(database -> !latestDatabaseNames.contains(database.getName()))
                .map(database -> new Object[] {getProjectId(database, currentProjectId, excludeSchemas),
                                               database.getId()})
                .collect(Collectors.toList());
            /**
             * just set existed to false if the database has been dropped instead of deleting it directly
             */
            if (!CollectionUtils.isEmpty(toDelete)) {
                String deleteSql = "update connect_database set is_existed = 0, project_id=? where id = ?";
                jdbcTemplate.batchUpdate(deleteSql, toDelete);
            }
            List<Object[]> toUpdate = existedDatabasesInDb.stream()
                .filter(database -> latestDatabaseNames.contains(database.getName()))
                .map(database -> {
                    DatabaseEntity latest = latestDatabaseName2Database.get(database.getName()).get(0);
                    return new Object[] {latest.getTableCount(), latest.getCollationName(), latest.getCharsetName(),
                                         getProjectId(database, currentProjectId, excludeSchemas), database.getId()};
                })
                .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(toUpdate)) {
                String update =
                    "update connect_database set table_count=?, collation_name=?, charset_name=?, project_id=? where "
                    + "id = ?";
                jdbcTemplate.batchUpdate(update, toUpdate);
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            log.warn("Failed to obtain the connection, errorMessage={}", e.getMessage());
            Throwable rootCause = e.getCause();
            if (rootCause instanceof SQLException) {
                deleteDatabaseIfClusterNotExists((SQLException) rootCause,
                    connection.getId(), "update connect_database set is_existed = 0 where connection_id=?");
                throw new IllegalStateException(rootCause);
            }
        } finally {
            try {
                executorService.shutdownNow();
            } catch (Exception e) {
                // eat the exception
            }
            if (teamDataSource instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) teamDataSource).close();
                } catch (Exception e) {
                    log.warn("Failed to close datasource, errorMessgae={}", e.getMessage());
                }
            }
        }
    }

    private Long getProjectId(DatabaseEntity database, Long currentProjectId, List<String> blockedDatabaseNames) {
        Long projectId;
        if (currentProjectId != null) {
            projectId = currentProjectId;
            if (dbSchemaSyncProperties.isBlockExclusionsWhenSyncDbToProject()
                    && blockedDatabaseNames.contains(database.getName())) {
                projectId = database.getProjectId();
            }
        } else {
            projectId = database.getProjectId();
        }
        return projectId;
    }

    /**
     * 同步个人数据源
     *
     * @param connection 数据库连接配置
     */
    private void syncIndividualDataSources(ConnectionConfig connection) {
        // 创建个人数据源
        DataSource individualDataSource = new OBConsoleDataSourceFactory(connection, true, false).getDataSource();
        // 创建线程池
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        // 提交任务并获取Future对象
        Future<Set<String>> future = executorService.submit(() -> {
            try (Connection conn = individualDataSource.getConnection()) {
                // 显示数据库
                return dbSchemaService.showDatabases(connection.getDialectType(), conn);
            }
        });
        try {
            // 获取最新的数据库名称集合
            Set<String> latestDatabaseNames = future.get(10, TimeUnit.SECONDS);
            // 获取数据库实体列表
            List<DatabaseEntity> existedDatabasesInDb = databaseRepository.findByConnectionId(connection.getId())
                    .stream().filter(DatabaseEntity::getExisted).collect(Collectors.toList());
            // 将数据库名称按照实体分组
            Map<String, List<DatabaseEntity>> existedDatabaseName2Database =
                    existedDatabasesInDb.stream().collect(Collectors.groupingBy(DatabaseEntity::getName));
            // 获取已存在的数据库名称集合
            Set<String> existedDatabaseNames = existedDatabaseName2Database.keySet();

            // 构建要添加的数据库实体列表
            List<Object[]> toAdd = latestDatabaseNames.stream()
                    .filter(latestDatabaseName -> !existedDatabaseNames.contains(latestDatabaseName))
                    .map(latestDatabaseName -> new Object[] {
                            com.oceanbase.odc.common.util.StringUtils.uuid(),
                            connection.getOrganizationId(),
                            latestDatabaseName,
                            connection.getId(),
                            connection.getEnvironmentId(),
                            DatabaseSyncStatus.SUCCEEDED.name(),
                            DBObjectSyncStatus.INITIALIZED.name()
                    })
                    .collect(Collectors.toList());

            // 批量插入数据库实体
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            if (CollectionUtils.isNotEmpty(toAdd)) {
                jdbcTemplate.batchUpdate(
                        "insert into connect_database(database_id, organization_id, name, connection_id, environment_id, "
                                + "sync_status, object_sync_status) values(?,?,?,?,?,?,?)",
                        toAdd);
            }

            // 构建要删除的数据库实体列表
            List<Object[]> toDelete =
                    existedDatabasesInDb.stream()
                            .filter(database -> !latestDatabaseNames.contains(database.getName()))
                            .map(database -> new Object[] {database.getId()})
                            .collect(Collectors.toList());
            // 批量删除数据库实体
            if (!CollectionUtils.isEmpty(toDelete)) {
                jdbcTemplate.batchUpdate("delete from connect_database where id = ?", toDelete);
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            log.warn("Failed to obtain the connection, errorMessage={}", e.getMessage());
            Throwable rootCause = e.getCause();
            if (rootCause instanceof SQLException) {
                deleteDatabaseIfClusterNotExists((SQLException) rootCause,
                        connection.getId(), "delete from connect_database where connection_id=?");
                throw new IllegalStateException(rootCause);
            }
        } finally {
            try {
                // 关闭线程池
                executorService.shutdownNow();
            } catch (Exception e) {
                // eat the exception
            }
            if (individualDataSource instanceof AutoCloseable) {
                try {
                    // 关闭个人数据源
                    ((AutoCloseable) individualDataSource).close();
                } catch (Exception e) {
                    log.warn("Failed to close datasource, errorMessgae={}", e.getMessage());
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal usage")
    public int deleteByDataSourceIds(@NonNull Set<Long> dataSourceId) {
        List<Long> databaseIds = databaseRepository.findByConnectionIdIn(dataSourceId).stream()
                .map(DatabaseEntity::getId).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(databaseIds)) {
            return 0;
        }
        deleteDatabaseRelatedPermissionByIds(databaseIds);
        dbColumnRepository.deleteByDatabaseIdIn(databaseIds);
        dbObjectRepository.deleteByDatabaseIdIn(databaseIds);
        return databaseRepository.deleteByConnectionIds(dataSourceId);
    }

    @Transactional(rollbackFor = Exception.class)
    @SkipAuthorize("internal usage")
    public int deleteByDataSourceId(@NonNull Long dataSourceId) {
        List<Long> databaseIds = databaseRepository.findByConnectionId(dataSourceId).stream()
                .map(DatabaseEntity::getId).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(databaseIds)) {
            return 0;
        }
        deleteDatabaseRelatedPermissionByIds(databaseIds);
        dbColumnRepository.deleteByDatabaseIdIn(databaseIds);
        dbObjectRepository.deleteByDatabaseIdIn(databaseIds);
        return databaseRepository.deleteByConnectionId(dataSourceId);
    }

    @SkipAuthorize("internal usage")
    public List<AuthorizedDatabase> getAllAuthorizedDatabases(@NonNull Long dataSourceId) {
        List<Database> databases = listDatabasesByConnectionIds(Collections.singleton(dataSourceId));
        Map<Long, Set<DatabasePermissionType>> id2Types = permissionHelper
                .getDBPermissions(databases.stream().map(Database::getId).collect(Collectors.toList()));
        return databases.stream().map(d -> new AuthorizedDatabase(d.getId(), d.getName(), id2Types.get(d.getId())))
                .collect(Collectors.toList());
    }

    @SkipAuthorize("internal authorized")
    public Page<DatabaseUser> listUserForOsc(Long dataSourceId) {
        ConnectionConfig config = connectionService.getForConnectionSkipPermissionCheck(dataSourceId);
        horizontalDataPermissionValidator.checkCurrentOrganization(config);
        DefaultConnectSessionFactory factory = new DefaultConnectSessionFactory(config);
        ConnectionSession connSession = factory.generateSession();
        try {
            OscDBAccessor dbSchemaAccessor = new OscDBAccessorFactory().generate(connSession);
            List<DBUser> dbUsers = dbSchemaAccessor.listUsers(null);
            Set<String> whiteUsers = OscDBUserUtil.getLockUserWhiteList(config);

            return new PageImpl<>(dbUsers.stream()
                    .filter(u -> !whiteUsers.contains(u.getName()))
                    .map(d -> DatabaseUser.builder().name(d.getNameWithHost()).build())
                    .collect(Collectors.toList()));
        } finally {
            connSession.expire();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @PreAuthenticate(hasAnyResourceRole = {"OWNER", "DBA"}, resourceType = "ODC_PROJECT", indexOfIdParam = 0)
    public boolean modifyDatabasesOwners(@NotNull Long projectId, @NotNull @Valid ModifyDatabaseOwnerReq req) {
        databaseRepository.findByIdIn(req.getDatabaseIds()).forEach(database -> {
            if (!projectId.equals(database.getProjectId())) {
                throw new AccessDeniedException();
            }
        });
        Set<Long> memberIds = resourceRoleService.listByResourceTypeAndId(ResourceType.ODC_PROJECT, projectId).stream()
                .map(UserResourceRole::getUserId).collect(Collectors.toSet());
        if (!memberIds.containsAll(req.getOwnerIds())) {
            throw new AccessDeniedException();
        }
        resourceRoleService.deleteByResourceTypeAndIdIn(ResourceType.ODC_DATABASE, req.getDatabaseIds());
        List<UserResourceRole> userResourceRoles = new ArrayList<>();
        req.getDatabaseIds().forEach(databaseId -> {
            userResourceRoles.addAll(req.getOwnerIds().stream().map(userId -> {
                UserResourceRole userResourceRole = new UserResourceRole();
                userResourceRole.setUserId(userId);
                userResourceRole.setResourceId(databaseId);
                userResourceRole.setResourceType(ResourceType.ODC_DATABASE);
                userResourceRole.setResourceRole(ResourceRoleName.OWNER);
                return userResourceRole;
            }).collect(Collectors.toList()));
        });
        resourceRoleService.saveAll(userResourceRoles);
        return true;
    }

    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public void updateObjectSyncStatus(@NotNull Collection<Long> databaseIds, @NotNull DBObjectSyncStatus status) {
        if (CollectionUtils.isEmpty(databaseIds)) {
            return;
        }
        databaseRepository.setObjectSyncStatusByIdIn(databaseIds, status);
    }

    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public void updateObjectLastSyncTimeAndStatus(@NotNull Long databaseId,
            @NotNull DBObjectSyncStatus status) {
        databaseRepository.setObjectLastSyncTimeAndStatusById(databaseId, new Date(), status);
    }

    /**
     * 刷新过期的待处理数据库对象状态 可以被ODC内部调用
     */
    @SkipAuthorize("odc internal usage")
    @Transactional(rollbackFor = Exception.class)
    public void refreshExpiredPendingDBObjectStatus() {
        // 计算同步日期
        Date syncDate = new Date(System.currentTimeMillis() - this.globalSearchProperties.getMaxPendingMillis());
        // 根据同步状态和最后同步时间设置对象同步状态
        int affectRows = this.databaseRepository.setObjectSyncStatusByObjectSyncStatusAndObjectLastSyncTimeBefore(
                DBObjectSyncStatus.INITIALIZED, DBObjectSyncStatus.PENDING, syncDate);
        // 记录日志
        log.info("Refresh outdated pending objects status, syncDate={}, affectRows={}", syncDate, affectRows);
    }

    private void checkPermission(Long projectId, Long dataSourceId) {
        if (Objects.isNull(projectId) && Objects.isNull(dataSourceId)) {
            throw new AccessDeniedException("invalid projectId or dataSourceId");
        }
        boolean isProjectMember = false;
        if (Objects.nonNull(projectId)) {
            isProjectMember = projectPermissionValidator.hasProjectRole(projectId, ResourceRoleName.all());
        }
        boolean canUpdateDataSource = false;
        if (Objects.nonNull(dataSourceId)) {
            canUpdateDataSource = connectionService.checkPermission(dataSourceId, Arrays.asList("update"));
        }
        if (!isProjectMember && !canUpdateDataSource) {
            throw new AccessDeniedException("invalid projectId or dataSourceId");
        }
    }

    private void checkTransferable(@NonNull Collection<DatabaseEntity> databases, @NonNull TransferDatabasesReq req) {
        if (CollectionUtils.isEmpty(databases)) {
            return;
        }
        if (Objects.nonNull(req.getProjectId())) {
            projectPermissionValidator.checkProjectRole(req.getProjectId(),
                    Arrays.asList(ResourceRoleName.DBA, ResourceRoleName.OWNER));
        }
        if (CollectionUtils.isNotEmpty(req.getOwnerIds())) {
            Set<Long> memberIds =
                    resourceRoleService.listByResourceTypeAndId(ResourceType.ODC_PROJECT, req.getProjectId()).stream()
                            .map(UserResourceRole::getUserId).collect(Collectors.toSet());
            PreConditions.validArgumentState(memberIds.containsAll(req.getOwnerIds()), ErrorCodes.AccessDenied, null,
                    "Invalid ownerIds");
        }
        List<Long> projectIds = databases.stream().map(DatabaseEntity::getProjectId).collect(Collectors.toList());
        List<Long> connectionIds = databases.stream().map(DatabaseEntity::getConnectionId).collect(Collectors.toList());
        projectPermissionValidator.checkProjectRole(projectIds,
                Arrays.asList(ResourceRoleName.DBA, ResourceRoleName.OWNER));
        PreConditions.validArgumentState(
                connectionService.checkPermission(connectionIds, Collections.singletonList("update")),
                ErrorCodes.AccessDenied, null, "Lack of update permission on current datasource");
        Map<Long, ConnectionConfig> id2Conn = connectionService.innerListByIds(connectionIds).stream()
                .collect(Collectors.toMap(ConnectionConfig::getId, c -> c, (c1, c2) -> c2));
        if (dbSchemaSyncProperties.isBlockExclusionsWhenSyncDbToProject()) {
            connectionIds = databases.stream().filter(database -> {
                ConnectionConfig connection = id2Conn.get(database.getConnectionId());
                return connection != null && !dbSchemaSyncProperties.getExcludeSchemas(connection.getDialectType())
                        .contains(database.getName());
            }).map(DatabaseEntity::getConnectionId).collect(Collectors.toList());
        }
        connectionIds.forEach(c -> {
            ConnectionConfig connection = id2Conn.get(c);
            if (connection == null) {
                throw new NotFoundException(ResourceType.ODC_CONNECTION, "id", c);
            }
            PreConditions.validArgumentState(connection.getProjectId() == null, ErrorCodes.AccessDenied, null,
                    "Cannot transfer databases in datasource which is bound to project");
        });
    }

    /**
     * 将实体列表转换为模型列表
     *
     * @param entities 实体列表
     * @param includesPermittedAction 是否包含允许的操作
     * @return 模型列表
     */
    private Page<Database> entitiesToModels(Page<DatabaseEntity> entities, boolean includesPermittedAction) {
        // 如果实体列表为空，则返回一个空的页面
        if (CollectionUtils.isEmpty(entities.getContent())) {
            return Page.empty();
        }
        // 获取项目ID和项目列表的映射关系
        Map<Long, List<Project>> projectId2Projects = projectService.mapByIdIn(entities.stream()
                .map(DatabaseEntity::getProjectId).collect(Collectors.toSet()));
        // 获取连接配置ID和连接配置列表的映射关系
        Map<Long, List<ConnectionConfig>> connectionId2Connections = connectionService.mapByIdIn(entities.stream()
                .map(DatabaseEntity::getConnectionId).collect(Collectors.toSet()));
        // 获取数据库ID和允许的操作类型的映射关系
        Map<Long, Set<DatabasePermissionType>> databaseId2PermittedActions = new HashMap<>();
        Set<Long> databaseIds = entities.stream().map(DatabaseEntity::getId).collect(Collectors.toSet());
        // 设置允许的操作类型
        if (includesPermittedAction) {
            databaseId2PermittedActions = permissionHelper.getDBPermissions(databaseIds);
        }
        // 将最终的数据库ID和允许的操作类型的映射关系存储到一个变量中
        Map<Long, Set<DatabasePermissionType>> finalId2PermittedActions = databaseId2PermittedActions;
        // 获取数据库ID和用户资源角色列表的映射关系
        Map<Long, List<UserResourceRole>> databaseId2UserResourceRole = new HashMap<>();
        // 获取用户ID和用户的映射关系
        Map<Long, User> userId2User = new HashMap<>();
        List<UserResourceRole> userResourceRoles =
                resourceRoleService.listByResourceTypeAndIdIn(ResourceType.ODC_DATABASE, databaseIds);
        if (CollectionUtils.isNotEmpty(userResourceRoles)) {
            databaseId2UserResourceRole = userResourceRoles.stream()
                    .collect(Collectors.groupingBy(UserResourceRole::getResourceId, Collectors.toList()));
            userId2User = userService
                    .batchNullSafeGet(
                            userResourceRoles.stream().map(UserResourceRole::getUserId).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(User::getId, v -> v, (v1, v2) -> v2));
        }
        // 将最终的数据库ID和用户资源角色列表的映射关系存储到一个变量中
        Map<Long, List<UserResourceRole>> finalDatabaseId2UserResourceRole = databaseId2UserResourceRole;
        // 将最终的用户ID和用户的映射关系存储到一个变量中
        Map<Long, User> finalUserId2User = userId2User;
        // 将实体列表转换为模型列表
        return entities.map(entity -> {
            Database database = databaseMapper.entityToModel(entity);
            // 获取项目列表
            List<Project> projects = projectId2Projects.getOrDefault(entity.getProjectId(), new ArrayList<>());
            // 获取连接配置列表
            List<ConnectionConfig> connections =
                    connectionId2Connections.getOrDefault(entity.getConnectionId(), new ArrayList<>());
            // 设置项目、环境和数据源
            database.setProject(CollectionUtils.isEmpty(projects) ? null : projects.get(0));
            database.setEnvironment(CollectionUtils.isEmpty(connections) ? null
                    : new Environment(connections.get(0).getEnvironmentId(), connections.get(0).getEnvironmentName(),
                            connections.get(0).getEnvironmentStyle()));
            database.setDataSource(CollectionUtils.isEmpty(connections) ? null : connections.get(0));
            // 设置允许的操作类型
            if (includesPermittedAction) {
                database.setAuthorizedPermissionTypes(finalId2PermittedActions.get(entity.getId()));
            }

            // Set the owner of the database
            // 设置数据库的所有者
            List<UserResourceRole> resourceRoles = finalDatabaseId2UserResourceRole.get(entity.getId());
            if (CollectionUtils.isNotEmpty(resourceRoles)) {
                Set<Long> ownerIds =
                        resourceRoles.stream().map(UserResourceRole::getUserId).collect(Collectors.toSet());
                List<InnerUser> owners = ownerIds.stream().map(id -> {
                    User user = finalUserId2User.get(id);
                    InnerUser innerUser = new InnerUser();
                    innerUser.setId(user.getId());
                    innerUser.setName(user.getName());
                    innerUser.setAccountName(user.getAccountName());
                    return innerUser;
                }).collect(Collectors.toList());
                database.setOwners(owners);
            }
            return database;
        });
    }

    private Database entityToModel(DatabaseEntity entity, boolean includesPermittedAction) {
        Database model = databaseMapper.entityToModel(entity);
        if (Objects.nonNull(entity.getProjectId())) {
            model.setProject(projectService.detail(entity.getProjectId()));
        }
        model.setDataSource(connectionService.getForConnectionSkipPermissionCheck(entity.getConnectionId()));
        model.setEnvironment(environmentService.detailSkipPermissionCheck(model.getDataSource().getEnvironmentId()));
        if (includesPermittedAction) {
            model.setAuthorizedPermissionTypes(
                    permissionHelper.getDBPermissions(Collections.singleton(entity.getId())).get(entity.getId()));
        }
        return model;
    }

    private void createDatabase(CreateDatabaseReq req, Connection conn, ConnectionConfig connection) {
        DBDatabase db = new DBDatabase();
        db.setName(req.getName());
        db.setCharset(req.getCharsetName());
        db.setCollation(req.getCollationName());
        SchemaPluginUtil.getDatabaseExtension(connection.getDialectType()).create(conn, db, connection.getPassword());
    }

    private void deleteDatabaseRelatedPermissionByIds(Collection<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        List<Long> permissionIds = userDatabasePermissionRepository.findByDatabaseIdIn(ids).stream()
                .map(UserDatabasePermissionEntity::getId).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(permissionIds)) {
            permissionRepository.deleteByIds(permissionIds);
            userPermissionRepository.deleteByPermissionIds(permissionIds);
        }
        permissionIds = userTablePermissionRepository.findByDatabaseIdIn(ids).stream()
                .map(UserTablePermissionEntity::getId).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(permissionIds)) {
            permissionRepository.deleteByIds(permissionIds);
            userPermissionRepository.deleteByPermissionIds(permissionIds);
        }
        resourceRoleService.deleteByResourceTypeAndIdIn(ResourceType.ODC_DATABASE, ids);
    }

    private List<UserResourceRole> buildUserResourceRoles(Collection<Long> databaseIds, Collection<Long> ownerIds) {
        List<UserResourceRole> userResourceRoles = new ArrayList<>();
        if (CollectionUtils.isEmpty(databaseIds) || CollectionUtils.isEmpty(ownerIds)) {
            return userResourceRoles;
        }
        databaseIds.forEach(databaseId -> {
            userResourceRoles.addAll(ownerIds.stream().map(userId -> {
                UserResourceRole userResourceRole = new UserResourceRole();
                userResourceRole.setUserId(userId);
                userResourceRole.setResourceId(databaseId);
                userResourceRole.setResourceType(ResourceType.ODC_DATABASE);
                userResourceRole.setResourceRole(ResourceRoleName.OWNER);
                return userResourceRole;
            }).collect(Collectors.toList()));
        });
        return userResourceRoles;
    }


    private void deleteDatabaseIfClusterNotExists(SQLException e, Long connectionId, String deleteSql) {
        if (StringUtils.containsIgnoreCase(e.getMessage(), "cluster not exist")) {
            log.info(
                    "Cluster not exist, set existed to false for all databases in this data source, data source id = {}",
                    connectionId);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            try {
                jdbcTemplate.update(deleteSql, new Object[] {connectionId});
            } catch (Exception ex) {
                log.warn("Failed to delete databases when cluster not exist, errorMessage={}",
                        ex.getLocalizedMessage());
            }
        }
    }
}
