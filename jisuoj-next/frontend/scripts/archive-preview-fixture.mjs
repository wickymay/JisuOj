// Local-only UI fixture. No database access; never used by the production app.
// Run with node scripts/archive-preview-fixture.mjs, then start Vite separately
// with VITE_DEV_API_TARGET=http://127.0.0.1:8082 on a different preview port.
import { createServer } from 'node:http'

const titles = ['星际航线的最短路径', '引力边界', '二进制信标', '穿越虫洞', '星图与最小生成树', '月面基地的补给计划', '深空广播', '回声序列', '寻找丢失的坐标', '同步轨道', '无限空间中的有限状态', '量子跃迁', '数组上的星辰', '未知星系的拓扑排序', '环形空间站', '能源分配', '失重的背包', '光年之外', '密码与质数', '边界条件', '归档与重建', '时间窗口', '银河网络', '最后一份档案']
const problems = titles.map((title, i) => ({ id: 2024 - i, title, source: i % 2 ? '算法训练' : '基础练习', accepted: 12 + i * 23, submit: 120 + i * 37, acceptanceRate: (12 + i * 23) / (120 + i * 37) * 100, userStatus: i % 3 === 0 ? 'AC' : i % 3 === 1 ? 'TRIED' : null }))
createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1:8082')
  const send = (body, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(body)) }
  if (url.pathname === '/api/auth/csrf') return send({ token: 'local-fixture', headerName: 'X-CSRF-TOKEN' })
  if (url.pathname === '/api/auth/me') { res.writeHead(204); return res.end() }
  if (url.pathname === '/api/problems/sources') return send(['基础练习', '算法训练'])
  if (url.pathname === '/api/problems') {
    const keyword = url.searchParams.get('keyword') || ''
    const source = url.searchParams.get('source') || ''
    if (keyword === 'error-fixture') return send({ message: '测试：资料服务暂时不可用' }, 503)
    const matches = problems.filter(p => (!keyword || p.title.includes(keyword) || String(p.id).includes(keyword)) && (!source || p.source === source))
    const page = Number(url.searchParams.get('page') || 1)
    const size = Number(url.searchParams.get('size') || 20)
    return send({ items: matches.slice((page - 1) * size, page * size), total: matches.length, page, size })
  }
  const id = Number(url.pathname.match(/^\/api\/problems\/(\d+)$/)?.[1])
  const problem = problems.find(p => p.id === id)
  if (problem) return setTimeout(() => send({ ...problem, description: `<p>在一片由 <strong>n 个节点</strong>构成的星际网络中，每条航线都有不同的能量消耗。</p><p>请设计一个算法，为探测器找出从起点到目标星系的最优路径。这是题目 ${id} 的测试简介。</p>`, timeLimit: 1 + id % 3, memoryLimit: 256, specialJudge: false }), id % 2 ? 80 : 450)
  return send({ message: '测试档案不存在' }, 404)
}).listen(8082, '127.0.0.1', () => console.log('Archive UI fixture: http://127.0.0.1:8082 (24 synthetic problems, no database)'))
