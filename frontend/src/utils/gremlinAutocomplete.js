import { autocompletion } from '@codemirror/autocomplete'

const GRAPH_SOURCE_METHODS = [
  { label: 'V()', detail: 'GraphTraversalSource', info: '从所有顶点开始遍历' },
  { label: 'E()', detail: 'GraphTraversalSource', info: '从所有边开始遍历' },
]

const TRAVERSAL_METHODS = [
  { label: 'out()', detail: 'vertex → vertex', info: '沿出边到达相邻顶点 (指定边标签可过滤)' },
  { label: 'in()', detail: 'vertex → vertex', info: '沿入边到达相邻顶点' },
  { label: 'both()', detail: 'vertex → vertex', info: '沿出入边到达相邻顶点' },
  { label: 'outE()', detail: 'vertex → edge', info: '获取出边 (可指定边标签)' },
  { label: 'inE()', detail: 'vertex → edge', info: '获取入边' },
  { label: 'bothE()', detail: 'vertex → edge', info: '获取出入边' },
  { label: 'outV()', detail: 'edge → vertex', info: '从边到达出点' },
  { label: 'inV()', detail: 'edge → vertex', info: '从边到达入点' },
  { label: 'bothV()', detail: 'edge → vertex', info: '从边到达出入点' },
  { label: 'hasLabel()', detail: 'filter', info: '按顶点/边标签过滤,如 hasLabel("Person")' },
  { label: 'has()', detail: 'filter', info: '按属性过滤,如 has("name","Alice") 或 has("age",gt(25))' },
  { label: 'hasNot()', detail: 'filter', info: '排除拥有指定属性的元素' },
  { label: 'hasId()', detail: 'filter', info: '按 ID 过滤' },
  { label: 'hasKey()', detail: 'filter', info: '按属性键名过滤' },
  { label: 'hasValue()', detail: 'filter', info: '按属性值过滤' },
  { label: 'limit()', detail: 'filter', info: '限制返回数量,如 limit(10)' },
  { label: 'range()', detail: 'filter', info: '范围过滤,如 range(0,10)' },
  { label: 'tail()', detail: 'filter', info: '取最后 N 个' },
  { label: 'dedup()', detail: 'filter', info: '去重' },
  { label: 'where()', detail: 'filter', info: '条件过滤,支持 P 谓词' },
  { label: 'is()', detail: 'filter', info: '值相等过滤,如 is("Alice")' },
  { label: 'not()', detail: 'filter', info: '排除匹配条件的遍历' },
  { label: 'simplePath()', detail: 'filter', info: '过滤掉含重复顶点的路径' },
  { label: 'cyclicPath()', detail: 'filter', info: '仅保留含环路的路径' },
  { label: 'order()', detail: 'filter', info: '排序,可指定 by() 排序键' },
  { label: 'valueMap()', detail: 'transform', info: '返回属性键值对 Map,传 true 包含 id/label' },
  { label: 'elementMap()', detail: 'transform', info: '返回含 id/label/properties 的 Map' },
  { label: 'values()', detail: 'transform', info: '获取指定属性的值,如 values("name")' },
  { label: 'keys()', detail: 'transform', info: '获取属性键名列表' },
  { label: 'label()', detail: 'transform', info: '获取标签名' },
  { label: 'id()', detail: 'transform', info: '获取元素 ID' },
  { label: 'path()', detail: 'transform', info: '返回遍历路径对象' },
  { label: 'select()', detail: 'transform', info: '选择已标记的遍历步骤,如 select("a")' },
  { label: 'as()', detail: 'transform', info: '为当前步骤设置标签,如 as("a")' },
  { label: 'project()', detail: 'transform', info: '投影为键值对,如 project("name","age").by("name").by("age")' },
  { label: 'map()', detail: 'transform', info: '对每个元素应用 lambda 转换' },
  { label: 'flatMap()', detail: 'transform', info: '一对多展开' },
  { label: 'group()', detail: 'transform', info: '按键分组,配合 by() 使用' },
  { label: 'groupCount()', detail: 'transform', info: '按键分组并计数' },
  { label: 'by()', detail: 'modifier', info: '指定排序/分组键,如 by("name") 或 by(label())' },
  { label: 'property()', detail: 'mutation', info: '设置属性 ⚠️ 写操作' },
  { label: 'properties()', detail: 'transform', info: '获取 Property 对象迭代器' },
  { label: 'count()', detail: 'terminal', info: '统计数量 (终端步骤)' },
  { label: 'sum()', detail: 'terminal', info: '求和' },
  { label: 'max()', detail: 'terminal', info: '最大值' },
  { label: 'min()', detail: 'terminal', info: '最小值' },
  { label: 'mean()', detail: 'terminal', info: '平均值' },
  { label: 'fold()', detail: 'terminal', info: '将所有结果折叠为单个 List' },
  { label: 'unfold()', detail: 'terminal', info: '将 List 展开为多个元素' },
  { label: 'toList()', detail: 'terminal', info: '收集为 List' },
  { label: 'iterate()', detail: 'terminal', info: '执行遍历并丢弃结果 ⚠️ 常用于 drop' },
  { label: 'addV()', detail: 'mutation', info: '添加顶点 ⚠️ 写操作' },
  { label: 'addE()', detail: 'mutation', info: '添加边 ⚠️ 写操作,如 addE("knows").from(...).to(...)' },
  { label: 'drop()', detail: 'mutation', info: '删除元素 ⚠️ 写操作' },
  { label: 'from()', detail: 'modifier', info: '指定边的起点 (addE) 或路径起点 (select)' },
  { label: 'to()', detail: 'modifier', info: '指定边的终点' },
  { label: 'sideEffect()', detail: 'sideEffect', info: '副作用操作 ⚠️ 管理员模式' },
  { label: 'constant()', detail: 'transform', info: '返回常量值' },
  { label: 'coin()', detail: 'filter', info: '以概率过滤,如 coin(0.5)' },
  { label: 'sample()', detail: 'filter', info: '随机采样,如 sample(10)' },
  { label: 'union()', detail: 'branch', info: '合并多个遍历结果' },
  { label: 'optional()', detail: 'branch', info: '可选遍历,无结果则保留原元素' },
  { label: 'repeat()', detail: 'branch', info: '循环遍历,配合 times()/until()' },
  { label: 'times()', detail: 'modifier', info: '指定 repeat 循环次数' },
  { label: 'until()', detail: 'modifier', info: '指定 repeat 循环终止条件' },
  { label: 'emit()', detail: 'modifier', info: '在 repeat 中发射中间结果' },
  { label: 'loops()', detail: 'filter', info: '按循环次数过滤' },
  { label: 'branch()', detail: 'branch', info: '条件分支遍历' },
  { label: 'choose()', detail: 'branch', info: '条件选择遍历路径' },
  { label: 'coalesce()', detail: 'branch', info: '返回第一个非空结果' },
  { label: 'aggregate()', detail: 'sideEffect', info: '聚合到指定集合' },
  { label: 'store()', detail: 'sideEffect', info: '存储到指定集合 (惰性)' },
  { label: 'inject()', detail: 'source', info: '注入常量值' },
  { label: 'subgraph()', detail: 'sideEffect', info: '将子图存入指定变量' },
  { label: 'tree()', detail: 'transform', info: '构建树形结构' },
  { label: 'cluster()', detail: 'transform', info: '聚类 (用于 OLAP)' },
]

const P_PREDICATES = [
  'eq()', 'neq()', 'gt()', 'gte()', 'lt()', 'lte()',
  'inside()', 'outside()', 'between()', 'within()', 'without()',
].map((label) => ({
  label,
  detail: 'P predicate',
  info: '比较谓词,用于 has() / where(),如 has("age", gt(25))',
}))

const ALL_TRAVERSAL_METHODS = [...GRAPH_SOURCE_METHODS, ...TRAVERSAL_METHODS, ...P_PREDICATES]

const VARIABLE_COMPLETIONS = [
  { label: 'g', detail: 'GraphTraversalSource', info: '图遍历源,所有查询的入口' },
  { label: 'graph', detail: 'SqlgGraph', info: '底层图对象,可访问 graph.tx() 等' },
  { label: 'P', detail: 'Predicate factory', info: '比较谓词工厂,如 P.gt(25) / P.within([...])' },
]

function gremlinCompletionSource(context) {
  const doc = context.state.doc.toString()
  const before = doc.substring(0, context.pos)

  const lastDotIdx = before.lastIndexOf('.')
  if (lastDotIdx >= 0) {
    const afterDot = before.substring(lastDotIdx + 1)
    const wsMatch = afterDot.match(/^(\s*)(\w*)$/)
    if (wsMatch) {
      const partial = wsMatch[2]
      const beforeExpr = before.substring(0, lastDotIdx).trim()
      const isGraphSource = beforeExpr === 'g'
      const candidates = isGraphSource ? GRAPH_SOURCE_METHODS : [...TRAVERSAL_METHODS, ...P_PREDICATES]
      const options = partial
        ? candidates.filter((c) => c.label.toLowerCase().startsWith(partial.toLowerCase()))
        : candidates
      return {
        from: context.pos - partial.length,
        options,
        validFor: /^\w*$/,
      }
    }
  }

  const word = context.matchBefore(/\b\w*$/)
  if (word) {
    if (word.text === '') return null
    const options = VARIABLE_COMPLETIONS.filter((c) =>
      c.label.toLowerCase().startsWith(word.text.toLowerCase())
    )
    if (options.length === 0) return null
    return {
      from: word.from,
      options,
      validFor: /^\w*$/,
    }
  }

  return null
}

export const gremlinAutocomplete = () => autocompletion({
  override: [gremlinCompletionSource],
  activateOnTyping: true,
  closeOnBlur: true,
})
