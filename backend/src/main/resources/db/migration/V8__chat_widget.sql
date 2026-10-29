-- 组件目录补充：AI 对话组件（第 9 个内置组件）
-- 对话本身无状态、不落库（与 Python Agent 无状态设计一致），仅注册组件目录。
INSERT INTO widget_definition (type, name, description, default_w, default_h, icon) VALUES
    ('chat', 'AI 对话', '基于 Workflow 编排的 AI 助手，可直接操控待办/便签/链接/算法题等组件', 6, 10, 'chat');
