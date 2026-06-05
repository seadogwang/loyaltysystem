import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Space, Card, Typography, Modal, Form, Input, Tree, message, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import PageWrapper from '../components/PageWrapper';
import api from '../api';

const { Title } = Typography;

interface PermissionNode {
  key: string;
  title: string;
  children?: PermissionNode[];
}

// 默认权限树
const DEFAULT_PERMISSIONS: PermissionNode[] = [
  {
    key: 'dashboard', title: '仪表盘',
    children: [
      { key: 'dashboard:view', title: '查看' },
    ],
  },
  {
    key: 'program', title: '俱乐部管理',
    children: [
      { key: 'program:read', title: '查看' },
      { key: 'program:write', title: '编辑' },
      { key: 'program:delete', title: '删除' },
    ],
  },
  {
    key: 'member', title: '会员管理',
    children: [
      { key: 'member:read', title: '查看' },
      { key: 'member:write', title: '编辑' },
      { key: 'member:delete', title: '删除' },
    ],
  },
  {
    key: 'points', title: '积分管理',
    children: [
      { key: 'points:read', title: '查看' },
      { key: 'points:write', title: '操作' },
    ],
  },
  {
    key: 'rule', title: '规则引擎',
    children: [
      { key: 'rule:read', title: '查看' },
      { key: 'rule:write', title: '编辑' },
      { key: 'rule:publish', title: '发布' },
    ],
  },
  {
    key: 'channel', title: '渠道集成',
    children: [
      { key: 'channel:read', title: '查看' },
      { key: 'channel:write', title: '配置' },
    ],
  },
  {
    key: 'system', title: '系统设置',
    children: [
      { key: 'system:read', title: '查看' },
      { key: 'system:admin', title: '管理' },
    ],
  },
];

const RoleManage: React.FC = () => {
  const [roles, setRoles] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<any>(null);
  const [checkedKeys, setCheckedKeys] = useState<string[]>([]);
  const [form] = Form.useForm();

  const fetchRoles = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get('/admin/roles');
      setRoles(data?.data || []);
    } catch (e: any) { setError(e.message || '加载失败'); setRoles([]); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchRoles(); }, [fetchRoles]);

  const handleSave = async (values: any) => {
    try {
      if (editingRole) {
        await api.put(`/admin/roles/${editingRole.id}`, { ...values, permissions: checkedKeys });
      } else {
        await api.post('/admin/roles', { ...values, permissions: checkedKeys });
      }
      message.success(editingRole ? '角色已更新' : '角色已创建');
      setModalOpen(false);
      form.resetFields();
      setCheckedKeys([]);
      setEditingRole(null);
      fetchRoles();
    } catch (e: any) { message.error(e.response?.data?.message || '保存失败'); }
  };

  const handleEdit = (role: any) => {
    setEditingRole(role);
    form.setFieldsValue(role);
    setCheckedKeys(role.permissions || []);
    setModalOpen(true);
  };

  const handleDelete = async (roleId: number) => {
    try {
      await api.delete(`/admin/roles/${roleId}`);
      message.success('角色已删除');
      fetchRoles();
    } catch (e: any) { message.error(e.response?.data?.message || '删除失败'); }
  };

  const columns = [
    { title: '角色名', dataIndex: 'name', width: 150 },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '权限数', dataIndex: 'permissions', width: 80,
      render: (v: string[]) => <Tag>{v?.length || 0}</Tag>,
    },
    { title: '创建时间', dataIndex: 'created_at', width: 140 },
    {
      title: '操作', width: 150,
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.id)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <PageWrapper loading={loading} error={error} onRetry={fetchRoles}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>角色权限管理</Title>
        <Button type="primary" icon={<PlusOutlined />}
          onClick={() => { setEditingRole(null); form.resetFields(); setCheckedKeys([]); setModalOpen(true); }}>
          新建角色
        </Button>
      </div>

      <Table dataSource={roles} columns={columns} loading={loading} rowKey="id" size="small"
        pagination={{ pageSize: 20 }} />

      <Modal
        title={editingRole ? '编辑角色' : '新建角色'}
        open={modalOpen}
        onCancel={() => { setModalOpen(false); setEditingRole(null); }}
        onOk={() => form.submit()}
        width={500}
      >
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item name="name" label="角色名称" rules={[{ required: true }]}>
            <Input placeholder="如：运营管理员" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item label="权限分配">
            <Tree
              checkable
              defaultExpandAll
              checkedKeys={checkedKeys}
              onCheck={(keys) => setCheckedKeys(keys as string[])}
              treeData={DEFAULT_PERMISSIONS}
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageWrapper>
  );
};

export default RoleManage;