import { useEffect, useState, useCallback } from 'react'
import {
  listIoConnections, setActiveConnection, getSchemas,
  exportVertices, exportEdges, exportTopology, importTopology,
  previewImport, importVertices, importEdges,
  refreshConnection,
} from '../api/io'
import {
  Database, Star, ChevronDown, ArrowLeftRight, RefreshCw,
  Download, Upload, Network, FileJson, FileSpreadsheet,
  CheckCircle2, AlertTriangle, X, Eye, Code2, FileText,
} from 'lucide-react'

export default function ImportExport() {
  const [connections, setConnections] = useState([])
  const [selectedConnId, setSelectedConnId] = useState(null)
  const [connDropdown, setConnDropdown] = useState(false)
  const [schemas, setSchemas] = useState([])
  const [activeTab, setActiveTab] = useState('export')
  const [toast, setToast] = useState(null)

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), 3500)
  }

  const loadConnections = useCallback(async () => {
    try {
      const res = await listIoConnections()
      const cl = res.data?.connections || []
      const remembered = res.data?.activeConnectionId
      setConnections(cl)
      if (cl.length > 0) {
        const fallback = cl.find((c) => c.isDefault) || cl[0]
        const initial = remembered != null && cl.some((c) => c.id === remembered) ? remembered : fallback.id
        setSelectedConnId((prev) => (prev == null ? initial : prev))
      }
    } catch (e) { showToast('error', e.message) }
  }, [])
  useEffect(() => { loadConnections() }, [loadConnections])

  const loadSchemas = useCallback(async (id) => {
    if (!id) { setSchemas([]); return }
    try {
      const res = await getSchemas(id)
      setSchemas(res.data || [])
    } catch (e) { showToast('error', e.message); setSchemas([]) }
  }, [])
  useEffect(() => { loadSchemas(selectedConnId) }, [selectedConnId, loadSchemas])

  const onRefresh = async () => {
    if (!selectedConnId) return
    try {
      await refreshConnection(selectedConnId)
      await loadSchemas(selectedConnId)
      showToast('success', '已刷新')
    } catch (e) { showToast('error', e.message) }
  }

  const selectedConn = connections.find((c) => c.id === selectedConnId)

  const vertexLabels = schemas.flatMap((s) =>
    (s.vertexLabels || []).map((vl) => ({ ...vl, schemaName: s.name }))
  )
  const edgeLabels = schemas.flatMap((s) =>
    (s.edgeLabels || []).map((el) => ({ ...el, schemaName: s.name }))
  )

  const downloadFile = (content, filename, binary) => {
    let blob
    if (binary) {
      const bytes = atob(content)
      const arr = new Uint8Array(bytes.length)
      for (let i = 0; i < bytes.length; i++) arr[i] = bytes.charCodeAt(i)
      blob = new Blob([arr])
    } else {
      blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <ArrowLeftRight size={20} className="text-indigo-500" /> 导入导出
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">导入/导出点边数据和 Topology 结构</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <button onClick={() => setConnDropdown((v) => !v)}
              className="flex min-w-[200px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50">
              <span className="flex items-center gap-2 truncate">
                <Database size={15} className="text-indigo-500" />
                <span className="truncate font-medium">{selectedConn ? selectedConn.name : '选择连接'}</span>
                {selectedConn?.isDefault && <Star size={13} className="fill-amber-400 text-amber-400" />}
              </span>
              <ChevronDown size={15} className="text-gray-400" />
            </button>
            {connDropdown && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setConnDropdown(false)} />
                <div className="absolute right-0 z-20 mt-1 max-h-72 w-64 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                  {connections.length === 0 && <div className="px-3 py-2 text-sm text-gray-400">暂无可用连接</div>}
                  {connections.map((c) => (
                    <button key={c.id} onClick={() => { setSelectedConnId(c.id); setConnDropdown(false); setActiveConnection(c.id).catch(() => {}) }}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-indigo-50 ${c.id === selectedConnId ? 'bg-indigo-50 text-indigo-700' : 'text-gray-700'}`}>
                      <span className="flex items-center gap-2 truncate"><Database size={14} className="text-gray-400" /><span className="truncate">{c.name}</span></span>
                      <span className="flex items-center gap-1"><span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">{c.dbType}</span>{c.isDefault && <Star size={12} className="fill-amber-400 text-amber-400" />}</span>
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
          <button onClick={onRefresh} disabled={!selectedConnId}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            <RefreshCw size={15} /> 刷新
          </button>
        </div>
      </header>

      <div className="flex items-center gap-1 border-b border-gray-200 bg-gray-50 px-4 pt-2">
        <TabButton active={activeTab === 'export'} onClick={() => setActiveTab('export')} icon={Download} label="数据导出" />
        <TabButton active={activeTab === 'import'} onClick={() => setActiveTab('import')} icon={Upload} label="数据导入" />
        <TabButton active={activeTab === 'topology'} onClick={() => setActiveTab('topology')} icon={Network} label="Topology" />
      </div>

      <div className="flex-1 overflow-auto p-6">
        {!selectedConnId ? (
          <div className="flex h-full items-center justify-center text-sm text-gray-400">请从右上角选择一个图数据库连接</div>
        ) : activeTab === 'export' ? (
          <ExportPanel vertexLabels={vertexLabels} edgeLabels={edgeLabels} connectionId={selectedConnId} onDownload={downloadFile} showToast={showToast} />
        ) : activeTab === 'import' ? (
          <ImportPanel vertexLabels={vertexLabels} edgeLabels={edgeLabels} connectionId={selectedConnId} showToast={showToast} />
        ) : (
          <TopologyPanel connectionId={selectedConnId} onDownload={downloadFile} showToast={showToast} />
        )}
      </div>

      {toast && (
        <div className={`fixed bottom-6 right-6 z-50 rounded-lg px-4 py-2.5 text-sm shadow-lg ${toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'}`}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

function ExportPanel({ vertexLabels, edgeLabels, connectionId, onDownload, showToast }) {
  const [selectedLabel, setSelectedLabel] = useState(null)
  const [labelType, setLabelType] = useState('vertex')
  const [format, setFormat] = useState('csv')
  const [exporting, setExporting] = useState(false)

  const onExport = async () => {
    if (!selectedLabel) { showToast('error', '请选择目标'); return }
    setExporting(true)
    try {
      const res = labelType === 'vertex'
        ? await exportVertices(connectionId, selectedLabel.schemaName, selectedLabel.name, format)
        : await exportEdges(connectionId, selectedLabel.schemaName, selectedLabel.name, format)
      onDownload(res.data.content, res.data.filename, res.data.binary)
      showToast('success', `已导出 ${res.data.rowCount} 条 (${format.toUpperCase()})`)
      if (res.data.truncated) showToast('error', '数据量超过上限,仅导出部分')
    } catch (e) { showToast('error', e.message) }
    finally { setExporting(false) }
  }

  const currentLabels = labelType === 'vertex' ? vertexLabels : edgeLabels

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <SectionCard title="选择导出目标" icon={Download}>
        <div className="mb-4 flex items-center gap-2">
          <button onClick={() => { setLabelType('vertex'); setSelectedLabel(null) }}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${labelType === 'vertex' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:bg-gray-100'}`}>
            点类型 ({vertexLabels.length})
          </button>
          <button onClick={() => { setLabelType('edge'); setSelectedLabel(null) }}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${labelType === 'edge' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:bg-gray-100'}`}>
            边类型 ({edgeLabels.length})
          </button>
        </div>

        <div className="mb-4 max-h-60 overflow-auto rounded-lg border border-gray-200">
          {currentLabels.length === 0 ? (
            <div className="px-4 py-8 text-center text-sm text-gray-400">暂无{labelType === 'vertex' ? '点' : '边'}类型</div>
          ) : currentLabels.map((l) => (
            <button key={`${l.schemaName}.${l.name}`} onClick={() => setSelectedLabel(l)}
              className={`flex w-full items-center justify-between px-4 py-2.5 text-sm hover:bg-gray-50
                ${selectedLabel?.name === l.name && selectedLabel?.schemaName === l.schemaName ? 'bg-indigo-50 text-indigo-700' : 'text-gray-700'}`}>
              <span className="flex items-center gap-2">
                <span className="font-medium">{l.name}</span>
                <span className="text-xs text-gray-400">{l.schemaName}</span>
              </span>
              <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">
                {(l.properties || []).length} prop
              </span>
            </button>
          ))}
        </div>

        {selectedLabel && (
          <div className="mb-4 rounded-lg bg-indigo-50 px-4 py-2 text-sm text-indigo-700">
            已选: {selectedLabel.schemaName}.{selectedLabel.name}
          </div>
        )}

        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-500">格式:</span>
          <div className="flex items-center gap-2">
            {[{ v: 'csv', l: 'CSV', icon: FileSpreadsheet }, { v: 'json', l: 'JSON', icon: FileJson }].map((f) => (
              <button key={f.v} onClick={() => setFormat(f.v)}
                className={`flex items-center gap-1 rounded-md px-3 py-1.5 text-sm ${format === f.v ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:bg-gray-100'}`}>
                <f.icon size={14} /> {f.l}
              </button>
            ))}
          </div>
        </div>
      </SectionCard>

      <div className="flex justify-end">
        <button onClick={onExport} disabled={!selectedLabel || exporting}
          className="flex items-center gap-1.5 rounded-md bg-indigo-600 px-6 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
          {exporting ? <RefreshCw size={15} className="animate-spin" /> : <Download size={15} />}
          {exporting ? '导出中...' : '执行导出'}
        </button>
      </div>
    </div>
  )
}

function ImportPanel({ vertexLabels, edgeLabels, connectionId, showToast }) {
  const [importType, setImportType] = useState('vertex')
  const [selectedLabel, setSelectedLabel] = useState(null)
  const [format, setFormat] = useState('csv')
  const [fileContent, setFileContent] = useState('')
  const [fileName, setFileName] = useState('')
  const [preview, setPreview] = useState(null)
  const [previewing, setPreviewing] = useState(false)
  const [importing, setImporting] = useState(false)
  const [importResult, setImportResult] = useState(null)

  const [overwrite, setOverwrite] = useState(false)
  const [outVertexLabel, setOutVertexLabel] = useState('')
  const [inVertexLabel, setInVertexLabel] = useState('')
  const [outVertexField, setOutVertexField] = useState('')
  const [inVertexField, setInVertexField] = useState('')
  const [fieldMapping, setFieldMapping] = useState({})

  const currentLabels = importType === 'vertex' ? vertexLabels : edgeLabels

  const onFileUpload = (e) => {
    const file = e.target.files[0]
    if (!file) return
    setFileName(file.name)
    const reader = new FileReader()
    reader.onload = (ev) => {
      setFileContent(ev.target.result)
      setPreview(null)
      setImportResult(null)
    }
    reader.readAsText(file)
  }

  const onPaste = (e) => {
    setFileContent(e.target.value)
    setFileName('pasted')
    setPreview(null)
    setImportResult(null)
  }

  const onPreview = async () => {
    if (!fileContent) { showToast('error', '请先上传或粘贴数据'); return }
    setPreviewing(true)
    try {
      const res = await previewImport(connectionId, { content: fileContent, format, type: importType })
      setPreview(res.data)
      if (importType === 'vertex' && selectedLabel) {
        const targetProps = (selectedLabel.properties || []).map((p) => p.name)
        const autoMap = {}
        ;(res.data.columns || []).forEach((col) => {
          const match = targetProps.find((p) => p.toLowerCase() === col.toLowerCase())
          if (match) autoMap[col] = match
        })
        setFieldMapping(autoMap)
      }
    } catch (e) { showToast('error', e.message) }
    finally { setPreviewing(false) }
  }

  const onImport = async () => {
    if (!fileContent || !selectedLabel) { showToast('error', '请先完成预览和配置'); return }
    if (importType === 'edge' && (!outVertexField || !inVertexField)) { showToast('error', '请指定出入点匹配字段'); return }
    setImporting(true)
    setImportResult(null)
    try {
      const res = importType === 'vertex'
        ? await importVertices(connectionId, {
            content: fileContent, format, schema: selectedLabel.schemaName, label: selectedLabel.name,
            fieldMapping, overwrite,
          })
        : await importEdges(connectionId, {
            content: fileContent, format, schema: selectedLabel.schemaName, label: selectedLabel.name,
            outVertexLabel, inVertexLabel, outVertexField, inVertexField, fieldMapping,
          })
      setImportResult(res.data)
      if (res.data.errors > 0) showToast('error', `导入完成: ${res.data.imported || 0} 成功, ${res.data.errors} 失败`)
      else showToast('success', `导入成功: ${res.data.imported || res.data.imported || 0} 条`)
    } catch (e) { showToast('error', e.message) }
    finally { setImporting(false) }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <SectionCard title="导入配置" icon={Upload}>
        <div className="mb-4 flex items-center gap-2">
          <button onClick={() => { setImportType('vertex'); setSelectedLabel(null); setPreview(null); setImportResult(null) }}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${importType === 'vertex' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:bg-gray-100'}`}>
            导入点 (Vertex)
          </button>
          <button onClick={() => { setImportType('edge'); setSelectedLabel(null); setPreview(null); setImportResult(null) }}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${importType === 'edge' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:bg-gray-100'}`}>
            导入边 (Edge)
          </button>
        </div>

        <div className="mb-4 grid grid-cols-2 gap-4">
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">目标 Label</label>
            <select value={selectedLabel ? `${selectedLabel.schemaName}.${selectedLabel.name}` : ''}
              onChange={(e) => {
                const label = currentLabels.find((l) => `${l.schemaName}.${l.name}` === e.target.value)
                setSelectedLabel(label || null); setPreview(null); setImportResult(null)
              }}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-indigo-500">
              <option value="">选择...</option>
              {currentLabels.map((l) => (
                <option key={`${l.schemaName}.${l.name}`} value={`${l.schemaName}.${l.name}`}>{l.schemaName}.{l.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-gray-500">文件格式</label>
            <div className="flex items-center gap-2">
              {[{ v: 'csv', l: 'CSV' }, { v: 'json', l: 'JSON' }].map((f) => (
                <button key={f.v} onClick={() => { setFormat(f.v); setPreview(null) }}
                  className={`rounded-md px-3 py-1.5 text-sm ${format === f.v ? 'bg-indigo-100 text-indigo-700' : 'text-gray-500 hover:bg-gray-100'}`}>
                  {f.l}
                </button>
              ))}
            </div>
          </div>
        </div>

        {importType === 'edge' && (
          <div className="mb-4 grid grid-cols-2 gap-4 rounded-lg border border-gray-200 p-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-500">出点 (Out Vertex) 类型</label>
              <input value={outVertexLabel} onChange={(e) => setOutVertexLabel(e.target.value)}
                placeholder="如 Person" className="w-full rounded border border-gray-300 px-2 py-1 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-500">出点匹配字段</label>
              <input value={outVertexField} onChange={(e) => setOutVertexField(e.target.value)}
                placeholder="如 object_id" className="w-full rounded border border-gray-300 px-2 py-1 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-500">入点 (In Vertex) 类型</label>
              <input value={inVertexLabel} onChange={(e) => setInVertexLabel(e.target.value)}
                placeholder="如 Person" className="w-full rounded border border-gray-300 px-2 py-1 text-sm" />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-500">入点匹配字段</label>
              <input value={inVertexField} onChange={(e) => setInVertexField(e.target.value)}
                placeholder="如 object_id" className="w-full rounded border border-gray-300 px-2 py-1 text-sm" />
            </div>
          </div>
        )}

        {importType === 'vertex' && (
          <div className="mb-4 flex items-center gap-2">
            <input type="checkbox" id="overwrite" checked={overwrite} onChange={(e) => setOverwrite(e.target.checked)}
              className="accent-indigo-600" />
            <label htmlFor="overwrite" className="text-sm text-gray-600">覆盖已有点 (按 identifier 匹配)</label>
          </div>
        )}
      </SectionCard>

      <SectionCard title="数据源" icon={FileText}>
        <div className="mb-3 flex items-center gap-2">
          <input type="file" accept={format === 'csv' ? '.csv' : '.json'}
            onChange={onFileUpload}
            className="hidden" id="file-upload" />
          <button onClick={() => document.getElementById('file-upload').click()}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            <Upload size={14} /> 选择文件
          </button>
          {fileName && <span className="text-xs text-gray-500">{fileName}</span>}
        </div>
        <textarea value={fileContent} onChange={onPaste}
          placeholder={`粘贴或上传 ${format.toUpperCase()} 数据...\n\nCSV 示例:\nname,age,object_id\nAlice,30,person_001\n\nJSON 示例:\n[{"name":"Alice","age":30,"object_id":"person_001"}]`}
          className="h-40 w-full rounded-lg border border-gray-300 p-3 font-mono text-xs outline-none focus:border-indigo-500" />
        <div className="mt-3 flex items-center gap-2">
          <button onClick={onPreview} disabled={!fileContent || previewing}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50">
            {previewing ? <RefreshCw size={14} className="animate-spin" /> : <Eye size={14} />} 预览
          </button>
        </div>
      </SectionCard>

      {preview && (
        <SectionCard title="预览结果" icon={Eye}>
          <div className="mb-3 flex items-center gap-4 text-xs text-gray-500">
            <span>解析到 {preview.totalRows} 行数据</span>
            <span className="text-red-500">{preview.errorCount} 个错误</span>
          </div>
          {preview.columns && preview.columns.length > 0 && (
            <div className="mb-3">
              <div className="mb-1 text-xs font-medium text-gray-400">检测到的列:</div>
              <div className="flex flex-wrap gap-1">
                {preview.columns.map((col) => (
                  <span key={col} className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">{col}</span>
                ))}
              </div>
            </div>
          )}
          {preview.errors && preview.errors.length > 0 && (
            <div className="mb-3 rounded-lg border border-red-200 bg-red-50 p-2">
              {preview.errors.map((err, i) => <div key={i} className="text-xs text-red-500">{err}</div>)}
            </div>
          )}
          {preview.rows && preview.rows.length > 0 && (
            <div className="overflow-auto rounded-lg border border-gray-200">
              <table className="min-w-full divide-y divide-gray-200 text-xs">
                <thead className="bg-gray-50 text-left text-gray-500">
                  <tr>{preview.columns.map((col) => <th key={col} className="px-2 py-1.5">{col}</th>)}</tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {preview.rows.slice(0, 10).map((row, i) => (
                    <tr key={i}>
                      {preview.columns.map((col) => <td key={col} className="px-2 py-1.5 text-gray-700">{row[col]}</td>)}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {importType === 'vertex' && selectedLabel && preview.columns && (
            <div className="mt-4">
              <div className="mb-2 text-sm font-medium text-gray-600">字段映射</div>
              <div className="space-y-1">
                {preview.columns.map((col) => (
                  <div key={col} className="flex items-center gap-2">
                    <span className="w-32 truncate text-xs text-gray-500">{col}</span>
                    <span className="text-gray-300">→</span>
                    <select value={fieldMapping[col] || ''}
                      onChange={(e) => setFieldMapping((prev) => ({ ...prev, [col]: e.target.value }))}
                      className="flex-1 rounded border border-gray-300 px-2 py-1 text-xs">
                      <option value="">(忽略)</option>
                      {(selectedLabel.properties || []).map((p) => (
                        <option key={p.name} value={p.name}>{p.name} ({p.type})</option>
                      ))}
                    </select>
                  </div>
                ))}
              </div>
            </div>
          )}
          {importType === 'edge' && preview.columns && (
            <div className="mt-4">
              <div className="mb-2 text-sm font-medium text-gray-600">边属性映射</div>
              <div className="space-y-1">
                {preview.columns.map((col) => (
                  <div key={col} className="flex items-center gap-2">
                    <span className="w-32 truncate text-xs text-gray-500">{col}</span>
                    <span className="text-gray-300">→</span>
                    <select value={fieldMapping[col] || ''}
                      onChange={(e) => setFieldMapping((prev) => ({ ...prev, [col]: e.target.value }))}
                      className="flex-1 rounded border border-gray-300 px-2 py-1 text-xs">
                      <option value="">(忽略)</option>
                      <option value={outVertexField}>{outVertexField || '(出点字段)'}</option>
                      <option value={inVertexField}>{inVertexField || '(入点字段)'}</option>
                      {(selectedLabel?.properties || []).map((p) => (
                        <option key={p.name} value={p.name}>{p.name} ({p.type})</option>
                      ))}
                    </select>
                  </div>
                ))}
              </div>
            </div>
          )}
        </SectionCard>
      )}

      {importResult && (
        <SectionCard title="导入结果" icon={importResult.errors > 0 ? AlertTriangle : CheckCircle2}>
          <div className="flex flex-wrap gap-4 text-sm">
            <StatCard label="总行数" value={importResult.totalRows} color="gray" />
            {importResult.imported != null && <StatCard label="新增" value={importResult.imported} color="green" />}
            {importResult.updated != null && <StatCard label="更新" value={importResult.updated} color="blue" />}
            <StatCard label="失败" value={importResult.errors} color="red" />
          </div>
          {importResult.errorMessages && importResult.errorMessages.length > 0 && (
            <div className="mt-3 rounded-lg border border-red-200 bg-red-50 p-3">
              <div className="mb-1 text-xs font-medium text-red-600">错误详情:</div>
              {importResult.errorMessages.map((msg, i) => <div key={i} className="text-xs text-red-500">{msg}</div>)}
            </div>
          )}
        </SectionCard>
      )}

      <div className="flex justify-end">
        <button onClick={onImport} disabled={!selectedLabel || !fileContent || importing}
          className="flex items-center gap-1.5 rounded-md bg-indigo-600 px-6 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
          {importing ? <RefreshCw size={15} className="animate-spin" /> : <Upload size={15} />}
          {importing ? '导入中...' : '执行导入'}
        </button>
      </div>
    </div>
  )
}

function TopologyPanel({ connectionId, onDownload, showToast }) {
  const [exporting, setExporting] = useState(false)
  const [importing, setImporting] = useState(false)
  const [topoContent, setTopoContent] = useState('')
  const [topoFileName, setTopoFileName] = useState('')
  const [importResult, setImportResult] = useState(null)

  const onExportTopology = async () => {
    setExporting(true)
    try {
      const res = await exportTopology(connectionId)
      onDownload(res.data.content, res.data.filename, false)
      showToast('success', `已导出 Topology (${res.data.rowCount} schemas)`)
    } catch (e) { showToast('error', e.message) }
    finally { setExporting(false) }
  }

  const onFileUpload = (e) => {
    const file = e.target.files[0]
    if (!file) return
    setTopoFileName(file.name)
    const reader = new FileReader()
    reader.onload = (ev) => {
      setTopoContent(ev.target.result)
      setImportResult(null)
    }
    reader.readAsText(file)
  }

  const onImportTopology = async () => {
    if (!topoContent) { showToast('error', '请先选择 Topology JSON 文件'); return }
    if (!window.confirm('确认导入 Topology? 将在目标连接中创建缺失的 Schema/Label/属性。')) return
    setImporting(true)
    setImportResult(null)
    try {
      const res = await importTopology(connectionId, { content: topoContent })
      setImportResult(res.data)
      const ok = res.data.errorCount || 0
      if (ok > 0) showToast('error', `导入完成: ${res.data.vertexLabelsCreated} 点类型, ${res.data.edgeLabelsCreated} 边类型, ${ok} 个错误`)
      else showToast('success', `导入成功: ${res.data.vertexLabelsCreated} 点类型, ${res.data.edgeLabelsCreated} 边类型, ${res.data.skipped} 跳过`)
    } catch (e) { showToast('error', e.message) }
    finally { setImporting(false) }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <SectionCard title="Topology 导出" icon={Download}>
        <p className="mb-4 text-sm text-gray-500">
          将当前连接的 Sqlg Topology 结构(Schema / VertexLabel / EdgeLabel / 属性 / 标识符)导出为 JSON 文件。
          可用于环境间迁移、备份或文档化。
        </p>
        <button onClick={onExportTopology} disabled={exporting}
          className="flex items-center gap-1.5 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
          {exporting ? <RefreshCw size={15} className="animate-spin" /> : <Download size={15} />}
          {exporting ? '导出中...' : '导出 Topology JSON'}
        </button>
      </SectionCard>

      <SectionCard title="Topology 导入 (从 JSON)" icon={Upload}>
        <p className="mb-4 text-sm text-gray-500">
          从导出的 Topology JSON 文件创建或恢复 Topology 结构。适用于环境间迁移、CI/CD 自动化部署等场景。
        </p>
        <div className="mb-4 flex items-center gap-2">
          <input type="file" accept=".json" onChange={onFileUpload} className="hidden" id="topo-file-upload" />
          <button onClick={() => document.getElementById('topo-file-upload').click()}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50">
            <Upload size={14} /> 选择 JSON 文件
          </button>
          {topoFileName && <span className="text-xs text-gray-500">{topoFileName}</span>}
        </div>
        {topoContent && (
          <div className="mb-4 max-h-48 overflow-auto rounded-lg border border-gray-200 bg-gray-50 p-3">
            <pre className="text-xs text-gray-600"><code>{topoContent.substring(0, 2000)}{topoContent.length > 2000 ? '\n...(截断)' : ''}</code></pre>
          </div>
        )}

        {importResult && (
          <div className="mb-4 rounded-lg border border-gray-200 p-4">
            <div className="mb-3 flex flex-wrap gap-4">
              <StatCard label="Schema" value={importResult.schemasCreated} color="blue" />
              <StatCard label="新增点类型" value={importResult.vertexLabelsCreated} color="green" />
              <StatCard label="新增边类型" value={importResult.edgeLabelsCreated} color="green" />
              <StatCard label="跳过(已存在)" value={importResult.skipped} color="gray" />
              <StatCard label="错误" value={importResult.errorCount} color="red" />
            </div>
            {importResult.errors && importResult.errors.length > 0 && (
              <div className="rounded-lg border border-red-200 bg-red-50 p-2">
                <div className="mb-1 text-xs font-medium text-red-600">错误详情:</div>
                {importResult.errors.map((err, i) => <div key={i} className="text-xs text-red-500">{err}</div>)}
              </div>
            )}
          </div>
        )}

        <button onClick={onImportTopology} disabled={!topoContent || importing}
          className="flex items-center gap-1.5 rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50">
          {importing ? <RefreshCw size={15} className="animate-spin" /> : <Upload size={15} />}
          {importing ? '导入中...' : '执行导入'}
        </button>
      </SectionCard>
    </div>
  )
}

function SectionCard({ title, icon: Icon, children }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 flex items-center gap-2 text-base font-semibold text-gray-800">
        <Icon size={18} className="text-indigo-500" /> {title}
      </h2>
      {children}
    </div>
  )
}

function StatCard({ label, value, color }) {
  const colors = {
    gray: 'bg-gray-50 text-gray-700',
    green: 'bg-green-50 text-green-700',
    blue: 'bg-blue-50 text-blue-700',
    red: 'bg-red-50 text-red-700',
  }
  return (
    <div className={`rounded-lg px-4 py-2 ${colors[color]}`}>
      <div className="text-xs opacity-70">{label}</div>
      <div className="text-xl font-bold">{value}</div>
    </div>
  )
}

function TabButton({ active, onClick, icon: Icon, label }) {
  return (
    <button onClick={onClick}
      className={`flex items-center gap-1.5 border-b-2 px-4 py-2 text-sm font-medium transition-colors
        ${active ? 'border-indigo-500 text-indigo-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
      <Icon size={15} /> {label}
    </button>
  )
}
