-- CalcForge core schema.
-- Design notes:
--  * Soft deletes: most tables carry a nullable deleted_at TIMESTAMP; application code
--    filters on "deleted_at IS NULL" rather than issuing physical DELETEs, so history is
--    recoverable and sync can propagate deletions safely.
--  * Local vs cloud: workspaces.user_id is nullable. NULL means the workspace lives only
--    on this device (local/anonymous, the default and required MVP experience); a value
--    means it is owned by a cloud account. Variables, formulas, calculations and scenarios
--    all hang off workspace_id, so they inherit local-vs-cloud from their workspace.
--  * All money/measurement values use DECIMAL, never FLOAT/DOUBLE, to avoid binary
--    floating-point rounding surprises in a product whose entire premise is correctness.

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- users
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(255)    NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP       NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- workspaces
CREATE TABLE workspaces (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NULL,
    name            VARCHAR(255)    NOT NULL,
    description     VARCHAR(2000)   NULL,
    is_shared       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP       NULL,
    CONSTRAINT fk_workspaces_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_workspaces_user ON workspaces (user_id, deleted_at);

-- ---------------------------------------------------------------- calculations (workspace canvas cards)
CREATE TABLE calculations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id    BIGINT          NOT NULL,
    label           VARCHAR(255)    NULL,
    expression      VARCHAR(2000)   NOT NULL,
    result          VARCHAR(255)    NULL,
    trail_json      JSON            NULL,
    position_index  INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP       NULL,
    CONSTRAINT fk_calculations_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_calculations_workspace ON calculations (workspace_id, deleted_at, position_index);

-- ---------------------------------------------------------------- variables
CREATE TABLE variables (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id    BIGINT          NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    value           DECIMAL(50,20)  NOT NULL,
    unit            VARCHAR(32)     NULL,
    description     VARCHAR(1000)   NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP       NULL,
    CONSTRAINT fk_variables_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT uq_variables_workspace_name UNIQUE (workspace_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_variables_workspace ON variables (workspace_id, deleted_at);

-- ---------------------------------------------------------------- formulas
CREATE TABLE formulas (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id    BIGINT          NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    expression      VARCHAR(2000)   NOT NULL,
    description     VARCHAR(1000)   NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP       NULL,
    CONSTRAINT fk_formulas_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT uq_formulas_workspace_name UNIQUE (workspace_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_formulas_workspace ON formulas (workspace_id, deleted_at);

-- ---------------------------------------------------------------- history_entries
CREATE TABLE history_entries (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NULL,
    workspace_id    BIGINT          NULL,
    expression      VARCHAR(2000)   NOT NULL,
    result          VARCHAR(255)    NULL,
    trail_json      JSON            NULL,
    tags            VARCHAR(500)    NULL,
    favorite        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP       NULL,
    CONSTRAINT fk_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_history_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_history_user_created ON history_entries (user_id, created_at);
CREATE INDEX idx_history_deleted ON history_entries (deleted_at);

-- ---------------------------------------------------------------- units (offline conversion database)
CREATE TABLE units (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    category            VARCHAR(64)         NOT NULL,
    name                VARCHAR(128)        NOT NULL,
    symbol              VARCHAR(32)         NOT NULL,
    to_base_factor      DECIMAL(38,18)      NOT NULL,
    to_base_offset      DECIMAL(38,18)      NOT NULL DEFAULT 0,
    is_base_unit        BOOLEAN             NOT NULL DEFAULT FALSE,
    sort_order          INT                 NOT NULL DEFAULT 0,
    CONSTRAINT uq_units_category_symbol UNIQUE (category, symbol)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_units_category ON units (category, sort_order);

-- ---------------------------------------------------------------- scenarios (what-if)
CREATE TABLE scenarios (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id    BIGINT          NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    variables_json  JSON            NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP       NULL,
    CONSTRAINT fk_scenarios_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_scenarios_workspace ON scenarios (workspace_id, deleted_at);

-- ---------------------------------------------------------------- sync_metadata
CREATE TABLE sync_metadata (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    client_id           VARCHAR(128)    NOT NULL,
    entity_type         VARCHAR(32)     NOT NULL,
    entity_id           BIGINT          NOT NULL,
    local_updated_at    TIMESTAMP       NOT NULL,
    remote_updated_at   TIMESTAMP       NULL,
    sync_status         VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sync_metadata_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_sync_metadata UNIQUE (user_id, entity_type, entity_id, client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------- refresh_tokens
CREATE TABLE refresh_tokens (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    token_hash      VARCHAR(255)    NOT NULL,
    expires_at      TIMESTAMP       NOT NULL,
    revoked         BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id, revoked);
