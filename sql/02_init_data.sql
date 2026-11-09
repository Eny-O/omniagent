-- ============================================================
-- OmniAgent 智能工单助手 - 最小种子数据
-- 用途：新库初始化，仅含字典与登录账号，不含演示工单
-- 可重复执行：INSERT 均使用 ON CONFLICT DO NOTHING
-- 默认密码：123456
-- ============================================================

-- 1. 部门（6，含层级）
INSERT INTO departments (id, dept_code, dept_name, parent_id, location, sort_order) VALUES
    (1, 'HQ',     '总公司',     NULL, '总部大楼1层大堂',   1),
    (2, 'IT',     '信息技术部', NULL, 'A栋3楼301机房',    2),
    (3, 'HR',     '人力资源部', NULL, 'B栋2楼人力资源中心', 3),
    (4, 'FIN',    '财务部',     NULL, 'B栋1楼财务室',     4),
    (5, 'IT_DEV', '开发组',     2,    'A栋3楼302',        1),
    (6, 'IT_OPS', '运维组',     2,    'A栋3楼303',        2)
ON CONFLICT DO NOTHING;

-- 2. 工单状态（7，id 与 TicketStatusEnum.dbId 对齐）
INSERT INTO ticket_statuses (id, status_code, status_name, is_terminal, sort_order) VALUES
    (1, 'DRAFT',      '草稿',   FALSE, 1),
    (2, 'PENDING',    '待处理', FALSE, 3),
    (3, 'PROCESSING', '处理中', FALSE, 4),
    (4, 'RESOLVED',   '已解决', FALSE, 5),
    (5, 'CLOSED',     '已关闭', TRUE,  6),
    (6, 'CANCELLED',  '已取消', TRUE,  7),
    (7, 'TO_ASSIGN',  '待派单', FALSE, 2)
ON CONFLICT DO NOTHING;

-- 3. 优先级（3，含响应/解决 SLA 分钟）
INSERT INTO ticket_priorities (id, priority_code, priority_name, response_sla_minutes, resolve_sla_minutes, sort_order) VALUES
    (1, 'LOW',    '低', 240, 2880, 1),
    (2, 'MEDIUM', '中', 120, 1440, 2),
    (3, 'HIGH',   '高', 60,  480,  3)
ON CONFLICT DO NOTHING;

-- 4. 工单分类（10，含二级）
INSERT INTO ticket_categories (id, category_code, category_name, parent_id, sort_order) VALUES
    (1,  'HARDWARE', '硬件故障', NULL, 1),
    (2,  'SOFTWARE', '软件问题', NULL, 2),
    (3,  'NETWORK',  '网络问题', NULL, 3),
    (4,  'OTHER',    '其他',     NULL, 4),
    (5,  'PC',       '电脑',     1,    1),
    (6,  'PRINTER',  '打印机',   1,    2),
    (7,  'OS',       '操作系统', 2,    1),
    (8,  'APP',      '应用软件', 2,    2),
    (9,  'WIFI',     '无线网络', 3,    1),
    (10, 'VPN',      'VPN 接入', 3,    2)
ON CONFLICT DO NOTHING;

-- 5. 工单来源（5，含 AGENT 智能助手）
INSERT INTO ticket_sources (id, source_code, source_name, sort_order) VALUES
    (1, 'WEB',   '网页',     1),
    (2, 'APP',   '移动端',   2),
    (3, 'EMAIL', '邮件',     3),
    (4, 'PHONE', '电话',     4),
    (5, 'AGENT', '智能助手', 5)
ON CONFLICT DO NOTHING;

-- 6. 用户（5，4 个业务角色；password_hash 为 BCrypt("123456")）
INSERT INTO users (id, username, password_hash, real_name, email, phone, dept_id, status, role) VALUES
    (1, 'admin',    '$2b$10$fur7uV5XDqbZuLi9F.anOe3icpJIz7RaPFzuAma4BBb1girS9Ji4K', '系统管理员', 'admin@omniagent.local',   '13800000001', 2, 1, 'ADMIN'),
    (2, 'zhangsan', '$2b$10$fur7uV5XDqbZuLi9F.anOe3icpJIz7RaPFzuAma4BBb1girS9Ji4K', '张三',       'zhangsan@example.com',    '13800000002', 1, 1, 'REPORTER'),
    (3, 'lisi',     '$2b$10$fur7uV5XDqbZuLi9F.anOe3icpJIz7RaPFzuAma4BBb1girS9Ji4K', '李四',       'lisi@omniagent.local',    '13800000003', 6, 1, 'HANDLER'),
    (4, 'wangwu',   '$2b$10$fur7uV5XDqbZuLi9F.anOe3icpJIz7RaPFzuAma4BBb1girS9Ji4K', '王五',       'wangwu@omniagent.local',  '13800000004', 3, 1, 'SUPERVISOR'),
    (5, 'zhaoliu',  '$2b$10$fur7uV5XDqbZuLi9F.anOe3icpJIz7RaPFzuAma4BBb1girS9Ji4K', '赵六',       'zhaoliu@example.com',     '13800000005', 1, 1, 'REPORTER')
ON CONFLICT DO NOTHING;

-- 7. 同步序列到当前 max(id)
SELECT setval(pg_get_serial_sequence('departments',       'id'), COALESCE((SELECT max(id) FROM departments),       1));
SELECT setval(pg_get_serial_sequence('ticket_statuses',   'id'), COALESCE((SELECT max(id) FROM ticket_statuses),   1));
SELECT setval(pg_get_serial_sequence('ticket_priorities', 'id'), COALESCE((SELECT max(id) FROM ticket_priorities), 1));
SELECT setval(pg_get_serial_sequence('ticket_categories', 'id'), COALESCE((SELECT max(id) FROM ticket_categories), 1));
SELECT setval(pg_get_serial_sequence('ticket_sources',    'id'), COALESCE((SELECT max(id) FROM ticket_sources),    1));
SELECT setval(pg_get_serial_sequence('users',             'id'), COALESCE((SELECT max(id) FROM users),             1));
