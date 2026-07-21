import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  listPropertyManagementConnections,
  setActiveConnection,
  getTree,
  refreshTree,
  listProperties,
  addProperty,
  updateProperty,
  removeProperty,
  createIndex,
  removeIndex,
  listPropertyTypes,
} from '../api/propertyManagement'
import {
  Database, Star, ChevronDown, ChevronRight, RefreshCw, Search,
  CircleDot, Minus, Boxes, Tags, Plus, Trash2, Key, X,
  ListFilter, Pencil,
} from 'lucide-react'

const emptyForm = {
  name: '',
  propertyType: 'STRING',
  displayName: '',
  listDisplay: false,
  searchable: false,
  indexType: 'NONE',
  remark: '',
}

export default function PropertyManagement() {
  const { t } = useTranslation('propertyManagement')
  const [connections, setConnections] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [tree, setTree] = useState(null)
  const [treeLoading, setTreeLoading] = useState(false)
  const [toast, setToast] = useState(null)

  const [treeSearch, setTreeSearch] = useState('')
  const [expanded, setExpanded] = useState(new Set())
  const [selectedLabel, setSelectedLabel] = useState(null)

  const [properties, setProperties] = useState([])
  const [propsLoading, setPropsLoading] = useState(false)

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState(emptyForm)
  const [saving, setSaving] = useState(false)
  const [editingProp, setEditingProp] = useState(null)
  const [propertyTypes, setPropertyTypes] = useState([])

  const showToast = (type, message) => {
    setToast({ type, message })
    setTimeout(() => setToast(null), 3500)
  }

  const loadConnections = useCallback(async () => {
    try {
      const res = await listPropertyManagementConnections()
      const cl = res.data?.connections || []
      const remembered = res.data?.activeConnectionId
      setConnections(cl)
      if (cl.length > 0) {
        const fallback = cl.find((c) => c.isDefault) || cl[0]
        const initial = remembered != null && cl.some((c) => c.id === remembered)
          ? remembered : fallback.id
        setSelectedId((prev) => (prev == null ? initial : prev))
      } else {
        setSelectedId(null)
      }
    } catch (e) {
      showToast('error', e.message)
    }
  }, [])

  useEffect(() => { loadConnections() }, [loadConnections])

  const loadTree = useCallback(async (id) => {
    if (!id) { setTree(null); return }
    setTreeLoading(true)
    try {
      const res = await getTree(id)
      setTree(res.data)
    } catch (e) {
      showToast('error', e.message)
      setTree(null)
    } finally {
      setTreeLoading(false)
    }
  }, [])

  useEffect(() => {
    loadTree(selectedId)
    setSelectedLabel(null)
    setProperties([])
  }, [selectedId, loadTree])

  const loadProperties = useCallback(async () => {
    if (!selectedLabel || !selectedId) { setProperties([]); return }
    setPropsLoading(true)
    try {
      const res = await listProperties(selectedId, selectedLabel.kind, selectedLabel.schema, selectedLabel.label)
      setProperties(res.data || [])
    } catch (e) {
      showToast('error', e.message)
      setProperties([])
    } finally {
      setPropsLoading(false)
    }
  }, [selectedId, selectedLabel])

  useEffect(() => { loadProperties() }, [loadProperties])

  useEffect(() => {
    if (propertyTypes.length === 0) {
      listPropertyTypes().then((res) => setPropertyTypes(res.data || [])).catch(() => {})
    }
  }, [propertyTypes])

  const selected = connections.find((c) => c.id === selectedId)

  const onSelect = async (id) => {
    setSelectedId(id)
    setDropdownOpen(false)
    try { await setActiveConnection(id) } catch (_) {}
  }

  const onRefresh = async () => {
    if (!selectedId) return
    try {
      await refreshTree(selectedId)
      await loadTree(selectedId)
      if (selectedLabel) await loadProperties()
      showToast('success', t('msg.refreshed'))
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

  const onLabelClick = (kind, schema, label) => {
    setSelectedLabel({ kind, schema, label })
    setExpanded((prev) => {
      const next = new Set(prev)
      next.add(`schema:${schema}`)
      return next
    })
  }

  const openCreate = () => {
    setEditingProp(null)
    setForm({ ...emptyForm })
    setModalOpen(true)
  }

  const openEdit = (prop) => {
    setEditingProp(prop)
    setForm({
      name: prop.name,
      propertyType: prop.propertyType,
      displayName: prop.displayName || '',
      listDisplay: prop.listDisplay,
      searchable: prop.searchable,
      indexType: prop.indexed ? (prop.indexType === 'UNIQUE' ? 'UNIQUE' : 'NON_UNIQUE') : 'NONE',
      remark: prop.remark || '',
    })
    setModalOpen(true)
  }

  const closeModal = () => {
    setModalOpen(false)
    setEditingProp(null)
  }

  const submit = async () => {
    if (!editingProp && (!form.name || !form.name.trim())) {
      showToast('error', t('msg.propNameRequired')); return
    }
    if (!selectedLabel) { showToast('error', t('msg.selectObjectFirst')); return }
    setSaving(true)
    try {
      if (editingProp) {
        await updateProperty(
          selectedId, selectedLabel.kind, selectedLabel.schema, selectedLabel.label,
          editingProp.name,
          {
            name: form.name.trim() || editingProp.name,
            displayName: form.displayName.trim() || null,
            listDisplay: form.listDisplay,
            searchable: form.searchable,
            createIndex: form.indexType !== 'NONE',
            indexType: form.indexType,
            remark: form.remark.trim() || null,
          }
        )
        showToast('success', t('msg.propUpdateOk'))
      } else {
        await addProperty(selectedId, selectedLabel.kind, selectedLabel.schema, selectedLabel.label, {
          name: form.name.trim(),
          propertyType: form.propertyType,
          displayName: form.displayName.trim() || null,
          listDisplay: form.listDisplay,
          searchable: form.searchable,
          createIndex: form.indexType !== 'NONE',
          indexType: form.indexType,
          remark: form.remark.trim() || null,
        })
        showToast('success', t('msg.propCreateOk'))
      }
      setModalOpen(false)
      setEditingProp(null)
      loadProperties()
    } catch (e) {
      showToast('error', e.message)
    } finally {
      setSaving(false)
    }
  }

  const onDeleteProp = async (prop) => {
    if (!window.confirm(t('msg.confirmDeleteProp', { name: prop.name, label: selectedLabel.label }))) return
    try {
      await removeProperty(selectedId, selectedLabel.kind, selectedLabel.schema, selectedLabel.label, prop.name)
      showToast('success', t('msg.propDeleted', { name: prop.name }))
      loadProperties()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const [indexPopover, setIndexPopover] = useState(null)

  const onCreateIndexConfirm = async (prop, unique) => {
    setIndexPopover(null)
    try {
      await createIndex(selectedId, selectedLabel.kind, selectedLabel.schema, selectedLabel.label, prop.name, unique)
      showToast('success', t('msg.indexCreated', { type: unique ? 'UNIQUE' : 'NON_UNIQUE', name: prop.name }))
      loadProperties()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const onRemoveIndex = async (prop) => {
    if (!window.confirm(t('msg.confirmRemoveIndex', { name: prop.name }))) return
    try {
      await removeIndex(selectedId, selectedLabel.kind, selectedLabel.schema, selectedLabel.label, prop.name)
      showToast('success', t('msg.indexRemoved', { name: prop.name }))
      loadProperties()
    } catch (e) {
      showToast('error', e.message)
    }
  }

  const filterTree = (schemas) => {
    if (!treeSearch.trim()) return schemas
    const kw = treeSearch.toLowerCase()
    return (schemas || []).map((s) => {
      const vl = (s.vertexLabels || []).filter((l) => l.toLowerCase().includes(kw) || s.name.toLowerCase().includes(kw))
      const el = (s.edgeLabels || []).filter((l) => l.toLowerCase().includes(kw) || s.name.toLowerCase().includes(kw))
      return { ...s, vertexLabels: vl, edgeLabels: el }
    }).filter((s) => s.name.toLowerCase().includes(kw) || (s.vertexLabels || []).length > 0 || (s.edgeLabels || []).length > 0)
  }

  const filteredSchemas = filterTree(tree?.schemas || [])

  const labelKindText = selectedLabel
    ? `${selectedLabel.kind === 'vertex' ? 'VertexLabel' : 'EdgeLabel'} ${selectedLabel.schema}.${selectedLabel.label}`
    : ''

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4">
        <div>
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <Tags size={20} className="text-amber-500" /> {t('title')}
          </h1>
          <p className="mt-0.5 text-sm text-gray-500">{t('subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <button
              onClick={() => setDropdownOpen((v) => !v)}
              className="flex min-w-[220px] items-center justify-between gap-2 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
            >
              <span className="flex items-center gap-2 truncate">
                <Database size={15} className="text-amber-500" />
                <span className="truncate font-medium">{selected ? selected.name : t('common:selectConnection')}</span>
                {selected?.isDefault && <Star size={13} className="fill-amber-400 text-amber-400" />}
              </span>
              <ChevronDown size={15} className="text-gray-400" />
            </button>
            {dropdownOpen && (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setDropdownOpen(false)} />
                <div className="absolute right-0 z-20 mt-1 max-h-72 w-64 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                  {connections.length === 0 && (
                    <div className="px-3 py-2 text-sm text-gray-400">{t('common:noConnection')}</div>
                  )}
                  {connections.map((c) => (
                    <button
                      key={c.id}
                      onClick={() => onSelect(c.id)}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-amber-50 ${
                        c.id === selectedId ? 'bg-amber-50 text-amber-700' : 'text-gray-700'
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
            disabled={!selectedId || treeLoading}
            className="flex items-center gap-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <RefreshCw size={15} className={treeLoading ? 'animate-spin' : ''} /> {t('refreshTopology')}
          </button>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        <aside className="flex w-72 flex-shrink-0 flex-col border-r border-gray-200 bg-white">
          <div className="border-b border-gray-100 p-3">
            <div className="relative">
              <Search size={15} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
              <input
                value={treeSearch}
                onChange={(e) => setTreeSearch(e.target.value)}
                placeholder={t('filterPlaceholder')}
                className="w-full rounded-md border border-gray-300 py-1.5 pl-8 pr-3 text-sm outline-none focus:border-amber-500"
              />
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-2">
            {!selectedId && (
              <div className="flex h-full items-center justify-center text-sm text-gray-400">{t('pleaseSelectConn')}</div>
            )}
            {selectedId && treeLoading && !tree && (
              <div className="flex h-full items-center justify-center text-sm text-gray-400">{t('common:loading')}</div>
            )}
            {selectedId && !treeLoading && filteredSchemas.length === 0 && (
              <div className="flex h-full items-center justify-center text-sm text-gray-400">
                {treeSearch ? t('noMatch') : t('noSchemaInConn')}
              </div>
            )}
            {filteredSchemas.map((schema) => {
              const sKey = `schema:${schema.name}`
              const sOpen = expanded.has(sKey) || !!treeSearch.trim()
              return (
                <div key={schema.name} className="mb-1">
                  <button
                    onClick={() => toggle(sKey)}
                    className="flex w-full items-center gap-1.5 rounded px-2 py-1 text-sm hover:bg-gray-50"
                  >
                    {sOpen ? <ChevronDown size={14} className="text-gray-400" /> : <ChevronRight size={14} className="text-gray-400" />}
                    <Boxes size={15} className="text-indigo-500" />
                    <span className="font-semibold text-gray-700">{schema.name}</span>
                  </button>
                  {sOpen && (
                    <div className="ml-2 border-l border-gray-100 pl-2">
                      {(schema.vertexLabels || []).length > 0 && (
                        <TreeGroup
                          title="VertexLabel"
                          icon={CircleDot}
                          color="text-blue-500"
                          items={schema.vertexLabels}
                          schemaName={schema.name}
                          kind="vertex"
                          selectedLabel={selectedLabel}
                          onLabelClick={onLabelClick}
                        />
                      )}
                      {(schema.edgeLabels || []).length > 0 && (
                        <TreeGroup
                          title="EdgeLabel"
                          icon={Minus}
                          color="text-pink-500"
                          items={schema.edgeLabels}
                          schemaName={schema.name}
                          kind="edge"
                          selectedLabel={selectedLabel}
                          onLabelClick={onLabelClick}
                        />
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </aside>

        <main className="flex-1 overflow-auto bg-gray-50 p-6">
          {!selectedLabel ? (
            <div className="flex h-full items-center justify-center text-sm text-gray-400">
              {t('selectFromTree')}
            </div>
          ) : (
            <div className="flex h-full flex-col">
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className={`flex h-10 w-10 items-center justify-center rounded-lg ${
                    selectedLabel.kind === 'vertex' ? 'bg-blue-50' : 'bg-pink-50'
                  }`}>
                    {selectedLabel.kind === 'vertex'
                      ? <CircleDot size={20} className="text-blue-500" />
                      : <Minus size={20} className="text-pink-500" />}
                  </div>
                  <div>
                    <div className="text-sm text-gray-400">{t('currentObject')}</div>
                    <div className="text-base font-semibold text-gray-800">
                      {selectedLabel.kind === 'vertex' ? 'VertexLabel' : 'EdgeLabel'}{' '}
                      <span className="text-amber-600">{selectedLabel.schema}.{selectedLabel.label}</span>
                    </div>
                  </div>
                </div>
                <button
                  onClick={openCreate}
                  className="flex items-center gap-1 rounded-md bg-amber-500 px-4 py-2 text-sm font-medium text-white hover:bg-amber-600"
                >
                  <Plus size={16} /> {t('addProperty')}
                </button>
              </div>

              <div className="flex-1 overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
                <div className="h-full overflow-auto">
                  <table className="min-w-full divide-y divide-gray-200 text-sm">
                    <thead className="sticky top-0 bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
                      <tr>
                        <th className="px-4 py-3">{t('col.propName')}</th>
                        <th className="px-4 py-3">{t('col.propType')}</th>
                        <th className="px-4 py-3">{t('col.dbType')}</th>
                        <th className="px-4 py-3 text-center">{t('col.identifier')}</th>
                        <th className="px-4 py-3 text-center">{t('col.index')}</th>
                        <th className="px-4 py-3 text-center">{t('col.searchable')}</th>
                        <th className="px-4 py-3 text-center">{t('col.listDisplay')}</th>
                        <th className="px-4 py-3">{t('col.displayName')}</th>
                        <th className="px-4 py-3 text-right">{t('common:actions')}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {propsLoading && (
                        <tr><td colSpan={9} className="px-4 py-12 text-center text-gray-400">{t('common:loading')}</td></tr>
                      )}
                      {!propsLoading && properties.length === 0 && (
                        <tr><td colSpan={9} className="px-4 py-12 text-center text-gray-400">{t('emptyProps')}</td></tr>
                      )}
                      {!propsLoading && properties.map((prop) => (
                        <tr key={prop.name} className="hover:bg-gray-50">
                          <td className="px-4 py-3 font-medium text-gray-800">{prop.name}</td>
                          <td className="px-4 py-3">
                            <span className="rounded bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700">{prop.propertyType}</span>
                          </td>
                          <td className="px-4 py-3">
                            {prop.dbType
                              ? <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600">{prop.dbType}</code>
                              : <span className="text-gray-300">—</span>}
                          </td>
                          <td className="px-4 py-3 text-center">
                            {prop.identifier
                              ? <Key size={15} className="mx-auto text-amber-500" />
                              : <span className="text-gray-300">—</span>}
                          </td>
                          <td className="px-4 py-3 text-center">
                            {prop.indexed
                              ? <span className="inline-flex items-center gap-1 rounded bg-green-50 px-1.5 py-0.5 text-xs text-green-700">
                                  {prop.indexType || 'INDEX'}
                                </span>
                              : <span className="text-gray-300">—</span>}
                          </td>
                          <td className="px-4 py-3 text-center">
                            {prop.searchable
                              ? <span className="text-green-600">✓</span>
                              : <span className="text-gray-300">—</span>}
                          </td>
                          <td className="px-4 py-3 text-center">
                            {prop.listDisplay
                              ? <ListFilter size={14} className="mx-auto text-blue-500" />
                              : <span className="text-gray-300">—</span>}
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            {prop.displayName || <span className="text-gray-300">—</span>}
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center justify-end gap-1">
                              <ActionBtn title={t('editProp')} onClick={() => openEdit(prop)}>
                                <Pencil size={14} />
                              </ActionBtn>
                              {!prop.indexed && !prop.identifier && (
                                <div className="relative">
                                  <ActionBtn title={t('createIndex')} color="amber" onClick={() => setIndexPopover(indexPopover === prop.name ? null : prop.name)}>
                                    <Key size={14} />
                                  </ActionBtn>
                                  {indexPopover === prop.name && (
                                    <>
                                      <div className="fixed inset-0 z-10" onClick={() => setIndexPopover(null)} />
                                      <div className="absolute right-0 z-20 mt-1 w-44 rounded-md border border-gray-200 bg-white py-1 shadow-lg">
                                        <div className="px-3 py-1 text-xs text-gray-400">{t('selectIndexType')}</div>
                                        <button
                                          onClick={() => onCreateIndexConfirm(prop, false)}
                                          className="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-amber-50"
                                        >
                                          <Key size={13} className="text-gray-400" />
                                          <div>
                                            <div className="font-medium">NON_UNIQUE</div>
                                            <div className="text-xs text-gray-400">{t('nonUnique')}</div>
                                          </div>
                                        </button>
                                        <button
                                          onClick={() => onCreateIndexConfirm(prop, true)}
                                          className="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-amber-50"
                                        >
                                          <Key size={13} className="text-green-500" />
                                          <div>
                                            <div className="font-medium">UNIQUE</div>
                                            <div className="text-xs text-gray-400">{t('uniqueIndex')}</div>
                                          </div>
                                        </button>
                                      </div>
                                    </>
                                  )}
                                </div>
                              )}
                              {prop.indexed && (
                                <ActionBtn title={t('removeIndex')} warning onClick={() => onRemoveIndex(prop)}>
                                  <Key size={14} />
                                </ActionBtn>
                              )}
                              <ActionBtn title={t('deleteProp')} danger onClick={() => onDeleteProp(prop)}>
                                <Trash2 size={14} />
                              </ActionBtn>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      {modalOpen && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
          <div className="max-h-[90vh] w-full max-w-lg overflow-hidden rounded-xl bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
              <h2 className="text-base font-semibold text-gray-800">
                {editingProp ? t('editTitle') : t('createTitle')}
              </h2>
              <button onClick={closeModal} className="text-gray-400 hover:text-gray-600">
                <X size={20} />
              </button>
            </div>

            <div className="max-h-[65vh] overflow-y-auto px-6 py-4">
              <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 px-4 py-2.5">
                <span className="text-xs text-gray-400">{t('currentObjectLabel')}</span>
                <span className="text-sm font-medium text-gray-700">{labelKindText}</span>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <Field label={t('col.propName')} required>
                  <input
                    className={inputCls}
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    placeholder={t('propNamePlaceholder')}
                  />
                </Field>
                <Field label={t('propTypeLabel')} required>
                  {editingProp ? (
                    <div className="flex items-center justify-between rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-600">
                      <span>{form.propertyType}</span>
                      <span className="text-xs text-gray-400">{t('propTypeReadonly')}</span>
                    </div>
                  ) : (
                    <select
                      className={inputCls}
                      value={form.propertyType}
                      onChange={(e) => setForm({ ...form, propertyType: e.target.value })}
                    >
                      {propertyTypes.map((tt) => (
                        <option key={tt} value={tt}>{tt}</option>
                      ))}
                    </select>
                  )}
                </Field>
              </div>

              <div className="mt-4">
                <Field label={t('displayName')}>
                  <input
                    className={inputCls}
                    value={form.displayName}
                    onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                    placeholder={t('displayNamePlaceholder')}
                  />
                </Field>
              </div>

              <div className="mt-4 grid grid-cols-2 gap-3">
                <CheckboxField
                  label={t('listDisplayLabel')}
                  checked={form.listDisplay}
                  onChange={(v) => setForm({ ...form, listDisplay: v })}
                />
                <CheckboxField
                  label={t('searchableLabel')}
                  checked={form.searchable}
                  onChange={(v) => setForm({ ...form, searchable: v })}
                />
              </div>

              <div className="mt-4">
                <label className="mb-2 block text-sm font-medium text-gray-700">{t('indexLabel')}</label>
                <div className="grid grid-cols-3 gap-3">
                  <RadioCard
                    label={t('notCreate')}
                    value="NONE"
                    currentValue={form.indexType}
                    onChange={(v) => setForm({ ...form, indexType: v })}
                  />
                  <RadioCard
                    label="NON_UNIQUE"
                    sublabel={t('nonUnique')}
                    value="NON_UNIQUE"
                    currentValue={form.indexType}
                    onChange={(v) => setForm({ ...form, indexType: v })}
                  />
                  <RadioCard
                    label="UNIQUE"
                    sublabel={t('uniqueIndex')}
                    value="UNIQUE"
                    currentValue={form.indexType}
                    onChange={(v) => setForm({ ...form, indexType: v })}
                  />
                </div>
              </div>

              <div className="mt-4">
                <Field label={t('common:remark')}>
                  <textarea
                    className={`${inputCls} h-20 resize-none`}
                    value={form.remark}
                    onChange={(e) => setForm({ ...form, remark: e.target.value })}
                    placeholder={t('remarkPlaceholder')}
                  />
                </Field>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 border-t border-gray-200 bg-gray-50 px-6 py-3">
              <button
                onClick={closeModal}
                className="rounded-md border border-gray-300 bg-white px-4 py-1.5 text-sm text-gray-600 hover:bg-gray-50"
              >
                {t('common:cancel')}
              </button>
              <button
                onClick={submit}
                disabled={saving}
                className="rounded-md bg-amber-500 px-4 py-1.5 text-sm font-medium text-white hover:bg-amber-600 disabled:opacity-50"
              >
                {saving ? t('common:saving') : t('common:save')}
              </button>
            </div>
          </div>
        </div>
      )}

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

function TreeGroup({ title, icon: Icon, color, items, schemaName, kind, selectedLabel, onLabelClick }) {
  return (
    <div className="mb-1">
      <div className="flex items-center gap-1.5 px-2 py-0.5 text-xs font-medium uppercase tracking-wider text-gray-400">
        <Icon size={12} className={color} /> {title} ({items.length})
      </div>
      <div className="ml-2">
        {items.map((label) => {
          const isSelected = selectedLabel
            && selectedLabel.schema === schemaName
            && selectedLabel.label === label
            && selectedLabel.kind === kind
          return (
            <button
              key={label}
              onClick={() => onLabelClick(kind, schemaName, label)}
              className={`flex w-full items-center gap-1.5 rounded px-2 py-1 text-sm transition-colors ${
                isSelected
                  ? 'bg-amber-100 text-amber-700 font-medium'
                  : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              <Icon size={13} className={isSelected ? 'text-amber-500' : color} />
              <span className="truncate">{label}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}

function Field({ label, required, children }) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-gray-700">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      {children}
    </div>
  )
}

function CheckboxField({ label, checked, onChange }) {
  return (
    <label className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2.5 text-sm transition-colors ${
      checked ? 'border-amber-300 bg-amber-50 text-amber-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50'
    }`}>
      <input
        type="checkbox"
        className="accent-amber-500"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span className="font-medium">{label}</span>
    </label>
  )
}

function RadioCard({ label, sublabel, value, currentValue, onChange }) {
  const active = value === currentValue
  return (
    <label className={`flex cursor-pointer flex-col items-center gap-0.5 rounded-md border px-3 py-2.5 text-center text-sm transition-colors ${
      active ? 'border-amber-300 bg-amber-50 text-amber-700' : 'border-gray-200 text-gray-600 hover:bg-gray-50'
    }`}>
      <input
        type="radio"
        className="hidden"
        checked={active}
        onChange={() => onChange(value)}
      />
      <Key size={14} className={active ? 'text-amber-500' : 'text-gray-400'} />
      <span className="font-medium">{label}</span>
      {sublabel && <span className="text-xs text-gray-400">{sublabel}</span>}
    </label>
  )
}

function ActionBtn({ title, onClick, disabled, danger, warning, color, children }) {
  return (
    <button
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={`flex h-7 w-7 items-center justify-center rounded transition-colors
        ${danger
          ? 'text-gray-500 hover:bg-red-50 hover:text-red-600'
          : warning
            ? 'text-gray-500 hover:bg-amber-50 hover:text-amber-600'
            : color === 'amber'
              ? 'text-gray-500 hover:bg-amber-50 hover:text-amber-600'
              : 'text-gray-500 hover:bg-indigo-50 hover:text-indigo-600'}
        ${disabled ? 'cursor-not-allowed opacity-40' : ''}`}
    >
      {children}
    </button>
  )
}

const inputCls = 'w-full rounded-md border border-gray-300 px-3 py-2 text-sm outline-none focus:border-amber-500 focus:ring-1 focus:ring-amber-100'
