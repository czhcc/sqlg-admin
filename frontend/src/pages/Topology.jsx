import { useEffect, useState, useCallback } from 'react'
import {
  listTopologyConnections,
  getTopology,
  refreshTopology,
  setActiveConnection,
} from '../api/topology'
import {
  ChevronDown, ChevronRight, Database, Star, RefreshCw,
  CircleDot, Minus, Settings2, Key, Boxes, Table2, GitFork,
} from 'lucide-react'

export default function Topology() {
  const [connections, setConnections] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [topology, setTopology] = useState(null)
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState(null)
  const [expanded, setExpanded] = useState(() => new Set(['schema:public', 'vertex']))

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), 3000)
  }

  const loadConnections = useCallback(async () => {
    try {
      const res = await listTopologyConnections()
      const list = res.data?.connections || []
      const remembered = res.data?.activeConnectionId
      setConnections(list)
      if (list.length > 0) {
        const fallback = list.find((c) => c.isDefault) || list[0]
        const initial = remembered != null && list.some((c) => c.id === remembered)
          ? remembered
          : fallback.id
        setSelectedId((prev) => prev == null ? initial : prev)
      } else {
        setSelectedId(null)
      }
    } catch (e) {
      showToast('error', e.message)
    }
  }, [])

  useEffect(() => { loadConnections() }, [loadConnections])

  const loadTopology = useCallback(async (id) => {
    if (!id) { setTopology(null); return }
    setLoading(true)
    try {
      const res = await getTopology(id)
      setTopology(res.data)
    } catch (e) {
      showToast('error', e.message)
      setTopology(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadTopology(selectedId) }, [selectedId, loadTopology])

  const selected = connections.find((c) => c.id === selectedId)

  const onSelect = async (id) => {
    setSelectedId(id)
    setDropdownOpen(false)
    try {
      await setActiveConnection(id)
    } catch (e) {
      showToast('error', '记住连接选择失败: ' + e.message)
    }
  }

  const onRefresh = async () => {
    if (!selectedId) return
    try {
      await refreshTopology(selectedId)
      await loadTopology(selectedId)
      showToast('success', '已刷新')
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const toggle = (key) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">Topology 浏览</h1>
          <p className="mt-0.5 text-sm text-gray-500">查看图数据库的 Schema / 点边类型 / 属性 / 索引</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <button
              onClick={() => setDropdownOpen((v) => !v)}
              className="flex min-w-[220px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
            >
              <span className="flex items-center gap-2 truncate">
                <Database size={15} className="text-indigo-500" />
                <span className="truncate font-medium">{selected ? selected.name : '选择连接'}</span>
                {selected?.isDefault && <Star size={13} className="fill-amber-400 text-amber-400" />}
              </span>
              <ChevronDown size={15} className="text-gray-400" />
            </button>
            {dropdownOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setDropdownOpen(false)} />
                <div className="absolute right-0 z-20 mt-1 max-h-72 w-64 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                  {connections.length === 0 && (
                    <div className="px-3 py-2 text-sm text-gray-400">暂无可用连接</div>
                  )}
                  {connections.map((c) => (
                    <button
                      key={c.id}
                      onClick={() => onSelect(c.id)}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-indigo-50 ${
                        c.id === selectedId ? 'bg-indigo-50 text-indigo-700' : 'text-gray-700'
                      }`}
                    >
                      <span className="flex items-center gap-2 truncate">
                        <Database size={14} className="text-gray-400" />
                        <span className="truncate">{c.name}</span>
                      </span>
                      <span className="flex items-center gap-1">
                        <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">{c.dbType}</span>
                        {c.isDefault && <Star size={12} className="fill-amber-400 text-amber-400" />}
                      </span>
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
          <button
            onClick={onRefresh}
            disabled={!selectedId || loading}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} /> 刷新
          </button>
        </div>
      </header>

      <div className="flex-1 overflow-auto p-6">
        {!selectedId && (
          <Empty text="请从右上角选择一个图数据库连接" />
        )}
        {selectedId && loading && !topology && (
          <Empty text="加载中..." />
        )}
        {selectedId && !loading && topology && (
          <TopologyTree topology={topology} expanded={expanded} toggle={toggle} />
        )}
      </div>

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

function Empty({ text }) {
  return (
    <div className="flex h-full items-center justify-center text-sm text-gray-400">
      {text}
    </div>
  )
}

function Row({ icon: Icon, label, badge, depth = 0 }) {
  return (
    <div className="flex items-center gap-2 py-1 text-sm" style={{ paddingLeft: depth * 18 }}>
      {Icon && <Icon size={14} className="text-gray-400" />}
      <span className="font-medium text-gray-700">{label}</span>
      {badge}
    </div>
  )
}

function Collapsible({ open, onClick, icon, label, badge, depth, children }) {
  const Icon = open ? ChevronDown : ChevronRight
  return (
    <div>
      <button
        onClick={onClick}
        className="flex w-full items-center gap-1.5 py-1 text-sm hover:bg-gray-50"
        style={{ paddingLeft: depth * 18 }}
      >
        <Icon size={14} className="text-gray-400" />
        {icon}
        <span className="font-semibold text-gray-800">{label}</span>
        {badge}
      </button>
      {open && <div>{children}</div>}
    </div>
  )
}

function Badge({ children, color = 'gray' }) {
  const colors = {
    gray:   'bg-gray-100 text-gray-600',
    indigo: 'bg-indigo-50 text-indigo-700',
    green:  'bg-green-50 text-green-700',
    amber:  'bg-amber-50 text-amber-700',
    blue:   'bg-blue-50 text-blue-700',
    pink:   'bg-pink-50 text-pink-700',
  }
  return (
    <span className={`ml-1.5 rounded px-1.5 py-0.5 text-[10px] font-medium ${colors[color]}`}>
      {children}
    </span>
  )
}

function TopologyTree({ topology, expanded, toggle }) {
  const schemas = topology.schemas || []
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center gap-2 border-b border-gray-100 pb-2 text-sm text-gray-500">
        <Boxes size={15} />
        <span className="font-medium text-gray-700">{topology.connectionName}</span>
        <Badge color="indigo">{topology.dbType}</Badge>
        <span>·</span>
        <span>{schemas.length} schema(s)</span>
      </div>

      {schemas.length === 0 && <Empty text="该连接没有任何 schema" />}

      {schemas.map((schema) => {
        const sKey = `schema:${schema.name}`
        const sOpen = expanded.has(sKey)
        const vCount = (schema.vertexLabels || []).length
        const eCount = (schema.edgeLabels || []).length
        return (
          <div key={schema.name} className="border-b border-gray-50 pb-2 last:border-0">
            <Collapsible
              open={sOpen}
              onClick={() => toggle(sKey)}
              icon={<Boxes size={15} className="text-indigo-500" />}
              label={`Schema: ${schema.name}`}
              depth={0}
              badge={<>
                <Badge color="blue">{vCount} 顶点</Badge>
                <Badge color="pink">{eCount} 边</Badge>
              </>}
            />
            {sOpen && (
              <div className="pt-1">
                <SchemaDetail schema={schema} expanded={expanded} toggle={toggle} />
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

function SchemaDetail({ schema, expanded, toggle }) {
  const vertexOpen = expanded.has(`vertex:${schema.name}`)
  const edgeOpen = expanded.has(`edge:${schema.name}`)
  return (
    <>
      <Collapsible
        open={vertexOpen}
        onClick={() => toggle(`vertex:${schema.name}`)}
        icon={<CircleDot size={14} className="text-blue-500" />}
        label="Vertex Labels"
        depth={1}
        badge={<Badge color="blue">{(schema.vertexLabels || []).length}</Badge>}
      />
      {vertexOpen && (schema.vertexLabels || []).map((v) => (
        <VertexLabelDetail key={v.fullName || v.name} vl={v} expanded={expanded} toggle={toggle} />
      ))}

      <Collapsible
        open={edgeOpen}
        onClick={() => toggle(`edge:${schema.name}`)}
        icon={<Minus size={14} className="text-pink-500" />}
        label="Edge Labels"
        depth={1}
        badge={<Badge color="pink">{(schema.edgeLabels || []).length}</Badge>}
      />
      {edgeOpen && (schema.edgeLabels || []).map((e) => (
        <EdgeLabelDetail key={e.fullName || e.name} el={e} expanded={expanded} toggle={toggle} />
      ))}
    </>
  )
}

function VertexLabelDetail({ vl, expanded, toggle }) {
  const k = `vl:${vl.fullName || vl.name}`
  const open = expanded.has(k)
  return (
    <Collapsible
      open={open}
      onClick={() => toggle(k)}
      depth={2}
      icon={<CircleDot size={13} className="text-blue-400" />}
      label={vl.name}
      badge={<>
        <Badge color="indigo">{vl.properties?.length || 0} prop</Badge>
        {vl.identifiers?.length > 0 && <Badge color="amber">{vl.identifiers.length} id</Badge>}
        {vl.partitioned && <Badge color="green">分区</Badge>}
        <Badge color="gray">in {vl.inEdgeLabels?.length || 0}</Badge>
        <Badge color="gray">out {vl.outEdgeLabels?.length || 0}</Badge>
      </>}
    >
      <SectionList depth={3} title="属性" icon={Settings2} color="text-gray-500">
        {(vl.properties || []).length === 0 ? <EmptyMini text="无属性" /> : vl.properties.map((p) => (
          <Row key={p.name} icon={Table2} label={p.name} depth={0} badge={<Badge color="indigo">{p.type}</Badge>} />
        ))}
      </SectionList>

      {(vl.identifiers || []).length > 0 && (
        <SectionList depth={3} title="Identifiers" icon={Key} color="text-amber-500">
          {vl.identifiers.map((id) => (
            <Row key={id} icon={Key} label={id} depth={0} badge={<Badge color="amber">identifier</Badge>} />
          ))}
        </SectionList>
      )}

      <SectionList depth={3} title="索引" icon={Settings2} color="text-gray-500">
        {(vl.indexes || []).length === 0 ? <EmptyMini text="无索引" /> : vl.indexes.map((idx) => (
          <Row
            key={idx.name}
            icon={Settings2}
            label={idx.name}
            depth={0}
            badge={<>
              <Badge color={idx.indexType === 'UNIQUE' ? 'green' : 'gray'}>{idx.indexType}</Badge>
              <Badge color="indigo">{(idx.properties || []).join(', ')}</Badge>
            </>}
          />
        ))}
      </SectionList>

      <SectionList depth={3} title="入边 (In Edges)" icon={GitFork} color="text-gray-500">
        {(vl.inEdgeLabels || []).length === 0 ? <EmptyMini text="无" /> : vl.inEdgeLabels.map((e) => (
          <Row key={e.fullName} icon={GitFork} label={e.fullName} depth={0} />
        ))}
      </SectionList>

      <SectionList depth={3} title="出边 (Out Edges)" icon={GitFork} color="text-gray-500">
        {(vl.outEdgeLabels || []).length === 0 ? <EmptyMini text="无" /> : vl.outEdgeLabels.map((e) => (
          <Row key={e.fullName} icon={GitFork} label={e.fullName} depth={0} />
        ))}
      </SectionList>
    </Collapsible>
  )
}

function EdgeLabelDetail({ el, expanded, toggle }) {
  const k = `el:${el.fullName || el.name}`
  const open = expanded.has(k)
  return (
    <Collapsible
      open={open}
      onClick={() => toggle(k)}
      depth={2}
      icon={<Minus size={13} className="text-pink-400" />}
      label={el.name}
      badge={<>
        <Badge color="indigo">{el.properties?.length || 0} prop</Badge>
        {el.partitioned && <Badge color="green">分区</Badge>}
      </>}
    >
      <SectionList depth={3} title="属性" icon={Settings2} color="text-gray-500">
        {(el.properties || []).length === 0 ? <EmptyMini text="无属性" /> : el.properties.map((p) => (
          <Row key={p.name} icon={Table2} label={p.name} depth={0} badge={<Badge color="indigo">{p.type}</Badge>} />
        ))}
      </SectionList>

      <SectionList depth={3} title="起点 (Out Vertex)" icon={CircleDot} color="text-blue-400">
        {(el.outVertexLabels || []).length === 0 ? <EmptyMini text="无" /> : el.outVertexLabels.map((v) => (
          <Row key={v} icon={CircleDot} label={v} depth={0} />
        ))}
      </SectionList>

      <SectionList depth={3} title="终点 (In vertex)" icon={CircleDot} color="text-blue-400">
        {(el.inVertexLabels || []).length === 0 ? <EmptyMini text="无" /> : el.inVertexLabels.map((v) => (
          <Row key={v} icon={CircleDot} label={v} depth={0} />
        ))}
      </SectionList>
    </Collapsible>
  )
}

function SectionList({ depth, title, icon: Icon, color, children }) {
  return (
    <div className="py-1" style={{ paddingLeft: depth * 16 }}>
      <div className="flex items-center gap-1.5 py-0.5 text-xs font-medium uppercase tracking-wider text-gray-400">
        <Icon size={12} className={color} /> {title}
      </div>
      <div className="ml-4 border-l border-gray-100 pl-2">
        {children}
      </div>
    </div>
  )
}

function EmptyMini({ text }) {
  return <div className="py-0.5 text-xs text-gray-300">{text}</div>
}
