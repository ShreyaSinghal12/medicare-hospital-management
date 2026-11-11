import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Banknote, CalendarCheck, CalendarX, FlaskConical, Hourglass, Stethoscope, UserPlus } from 'lucide-react';
import {
  Bar, BarChart, CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import ChartCard from '../../components/charts/ChartCard.jsx';
import ChartTooltip from '../../components/charts/ChartTooltip.jsx';
import Card from '../../components/ui/Card.jsx';
import ErrorState from '../../components/ui/ErrorState.jsx';
import { TextField } from '../../components/ui/FormField.jsx';
import PageHeader from '../../components/ui/PageHeader.jsx';
import Spinner from '../../components/ui/Spinner.jsx';
import StatCard from '../../components/ui/StatCard.jsx';
import useApi from '../../hooks/useApi.js';
import { getAdminDashboard } from '../../services/adminInsightsService.js';
import { addDaysIso, formatDateLong, formatDateShort, todayIsoLocal } from '../../utils/dates.js';
import { formatCurrency } from '../../utils/format.js';

// Single-series charts: one brand hue (validated against the light surface), recessive grid and axes.
const MARK = '#0d9488';
const GRID = '#e2e8f0';
const AXIS = { fontSize: 11, fill: '#64748b' };
const label = (s) => (s ? s.charAt(0) + s.slice(1).toLowerCase().replaceAll('_', ' ') : s);
/** Compact axis labels that stay distinct: ₹550, ₹1.6k, ₹2.2k, ₹12k, ₹1.5L. */
export function moneyTick(v) {
  const a = Math.abs(v);
  const sign = v < 0 ? '−' : '';
  if (a >= 100000) return `${sign}₹${+(a / 100000).toFixed(1)}L`;
  if (a >= 10000) return `${sign}₹${Math.round(a / 1000)}k`;
  if (a >= 1000) return `${sign}₹${+(a / 1000).toFixed(1)}k`;
  return `${sign}₹${a}`;
}
const shortDate = (iso) => formatDateShort(iso).replace(/,? \d{4}$/, '');

const PRESETS = [[7, 'Last 7 days'], [30, 'Last 30 days'], [90, 'Last 90 days']];

/** Administrator home: operational statistics (counts and amounts only, no clinical content). */
export default function AdminDashboardPage() {
  const today = todayIsoLocal();
  const [from, setFrom] = useState(addDaysIso(today, -29));
  const [to, setTo] = useState(today);
  const { data: d, loading, error, reload } = useApi(() => getAdminDashboard({ from, to }), [from, to]);
  const preset = PRESETS.find(([n]) => to === today && from === addDaysIso(today, -(n - 1)))?.[0];
  const statusRows = d ? Object.entries(d.appointmentsByStatus).map(([s, c]) => ({ name: label(s), count: c })) : [];

  return (
    <>
      <PageHeader title="Administration" description="Hospital activity for the selected period. Patient-level clinical data is never shown here." />
      <Card>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-wrap gap-1" role="group" aria-label="Period">
            {PRESETS.map(([n, text]) => (
              <button key={n} type="button" aria-pressed={preset === n}
                onClick={() => { setTo(today); setFrom(addDaysIso(today, -(n - 1))); }}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium ${preset === n ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}>
                {text}
              </button>
            ))}
          </div>
          <div className="grid grid-cols-2 gap-2">
            <TextField label="From" type="date" value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} />
            <TextField label="To" type="date" value={to} min={from} onChange={(e) => e.target.value && setTo(e.target.value)} />
          </div>
        </div>
      </Card>

      {loading && !d && <Spinner />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {d && (
        <>
          <p className="mt-4 text-sm text-slate-500">{formatDateLong(d.from)} – {formatDateLong(d.to)}</p>
          <div className="mt-2 grid grid-cols-2 gap-4 lg:grid-cols-4">
            <StatCard icon={CalendarCheck} label="Appointments" value={d.totalAppointments}
              hint={`${d.completedAppointments} completed`} />
            <StatCard icon={CalendarX} label="No-shows / cancelled" value={`${d.noShowAppointments} / ${d.cancelledAppointments}`} tone="amber" />
            <StatCard icon={UserPlus} label="New patients" value={d.newPatients} hint={`${d.activePatients} active in total`} />
            <StatCard icon={Stethoscope} label="Active doctors" value={d.activeDoctors} />
            <StatCard icon={Banknote} label="Collected" value={formatCurrency(d.collected)} hint={`${formatCurrency(d.refunded)} refunded`} />
            <StatCard icon={Hourglass} label="Outstanding (all dates)" value={formatCurrency(d.outstanding)} hint={`${d.outstandingInvoices} open invoice(s)`} tone="amber" />
            <Link to="/admin/inventory/alerts" className="contents">
              <StatCard icon={AlertTriangle} label="Stock alerts" value={d.lowStockMedicines + d.expiringBatches + d.expiredBatches}
                hint={`${d.lowStockMedicines} low · ${d.expiringBatches} expiring · ${d.expiredBatches} expired`} tone="amber" />
            </Link>
            <StatCard icon={FlaskConical} label="Lab orders" value={Object.values(d.labOrdersByStatus).reduce((a, b) => a + b, 0)}
              hint={Object.entries(d.labOrdersByStatus).map(([s, c]) => `${c} ${label(s).toLowerCase()}`).join(' · ') || 'none'} />
          </div>

          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <ChartCard title="Appointments per day" subtitle="Excluding cancelled"
              summary={`Appointments per day from ${d.from} to ${d.to}, total ${d.totalAppointments - d.cancelledAppointments}`}
              columns={['Date', 'Appointments']} rows={d.appointmentsPerDay.map((r) => [r.date, r.count])}>
              <ResponsiveContainer>
                <LineChart data={d.appointmentsPerDay} margin={{ top: 8, right: 12, bottom: 0, left: -16 }}>
                  <CartesianGrid stroke={GRID} vertical={false} />
                  <XAxis dataKey="date" tick={AXIS} tickFormatter={shortDate} minTickGap={24} axisLine={false} tickLine={false} />
                  <YAxis allowDecimals={false} tick={AXIS} axisLine={false} tickLine={false} />
                  <Tooltip cursor={{ stroke: '#94a3b8', strokeDasharray: '3 3' }}
                    content={<ChartTooltip labelFormat={formatDateLong} format={(v) => `${v} appointment(s)`} />} />
                  <Line isAnimationActive={false} type="linear" dataKey="count" stroke={MARK} strokeWidth={2} dot={false} activeDot={{ r: 4, strokeWidth: 2, stroke: '#fff' }} />
                </LineChart>
              </ResponsiveContainer>
            </ChartCard>

            <ChartCard title="Money collected per day" subtitle="Payments minus refunds"
              summary={`Net collected per day, total ${formatCurrency(d.collected - d.refunded)}`}
              columns={['Date', 'Net collected']} rows={d.revenuePerDay.map((r) => [r.date, formatCurrency(r.amount)])}>
              <ResponsiveContainer>
                <BarChart data={d.revenuePerDay} margin={{ top: 8, right: 12, bottom: 0, left: 4 }} barCategoryGap={2}>
                  <CartesianGrid stroke={GRID} vertical={false} />
                  <XAxis dataKey="date" tick={AXIS} tickFormatter={shortDate} minTickGap={24} axisLine={false} tickLine={false} />
                  <YAxis tick={AXIS} axisLine={false} tickLine={false} width={56}
                    tickFormatter={moneyTick} />
                  <ReferenceLine y={0} stroke="#94a3b8" />
                  <Tooltip cursor={{ fill: '#f1f5f9' }} content={<ChartTooltip labelFormat={formatDateLong} format={formatCurrency} />} />
                  <Bar isAnimationActive={false} dataKey="amount" fill={MARK} radius={[4, 4, 0, 0]} maxBarSize={24} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>

            <ChartCard title="Appointments by department" subtitle="Excluding cancelled"
              summary={d.appointmentsByDepartment.map((r) => `${r.name} ${r.count}`).join(', ')}
              columns={['Department', 'Appointments']} rows={d.appointmentsByDepartment.map((r) => [r.name, r.count])}
              empty={d.appointmentsByDepartment.length === 0 ? 'No appointments in this period.' : null}>
              <ResponsiveContainer>
                <BarChart data={d.appointmentsByDepartment.slice(0, 8)} layout="vertical" margin={{ top: 0, right: 24, bottom: 0, left: 8 }} barCategoryGap={6}>
                  <XAxis type="number" allowDecimals={false} tick={AXIS} axisLine={false} tickLine={false} />
                  <YAxis type="category" dataKey="name" tick={AXIS} width={110} axisLine={false} tickLine={false} />
                  <Tooltip cursor={{ fill: '#f1f5f9' }} content={<ChartTooltip format={(v) => `${v} appointment(s)`} />} />
                  <Bar isAnimationActive={false} dataKey="count" fill={MARK} radius={[0, 4, 4, 0]} maxBarSize={18} label={{ position: 'right', fontSize: 11, fill: '#334155' }} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>

            <ChartCard title="Appointments by status" summary={statusRows.map((r) => `${r.name} ${r.count}`).join(', ')}
              columns={['Status', 'Appointments']} rows={statusRows.map((r) => [r.name, r.count])}
              empty={statusRows.length === 0 ? 'No appointments in this period.' : null}>
              <ResponsiveContainer>
                <BarChart data={statusRows} layout="vertical" margin={{ top: 0, right: 24, bottom: 0, left: 8 }} barCategoryGap={6}>
                  <XAxis type="number" allowDecimals={false} tick={AXIS} axisLine={false} tickLine={false} />
                  <YAxis type="category" dataKey="name" tick={AXIS} width={90} axisLine={false} tickLine={false} />
                  <Tooltip cursor={{ fill: '#f1f5f9' }} content={<ChartTooltip format={(v) => `${v} appointment(s)`} />} />
                  <Bar isAnimationActive={false} dataKey="count" fill={MARK} radius={[0, 4, 4, 0]} maxBarSize={18} label={{ position: 'right', fontSize: 11, fill: '#334155' }} />
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>
          </div>

          <div className="mt-6">
            <Card title="Doctor workload" subtitle="Top 10 by appointments in the period (excluding cancelled)">
              {d.doctorWorkload.length === 0 ? <p className="text-sm text-slate-500">No appointments in this period.</p> : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[32rem] text-left text-sm">
                    <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                      <tr>
                        <th scope="col" className="py-2 pr-3 font-semibold">Doctor</th>
                        <th scope="col" className="py-2 pr-3 font-semibold">Department</th>
                        <th scope="col" className="py-2 pr-3 text-right font-semibold">Appointments</th>
                        <th scope="col" className="py-2 pr-3 text-right font-semibold">Completed</th>
                        <th scope="col" className="py-2 text-right font-semibold">No-shows</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {d.doctorWorkload.map((w) => {
                        const max = d.doctorWorkload[0].total || 1;
                        return (
                          <tr key={w.doctorId}>
                            <td className="py-2 pr-3 font-medium text-slate-900">{w.doctorName}</td>
                            <td className="py-2 pr-3 text-slate-600">{w.department}</td>
                            <td className="py-2 pr-3">
                              <div className="flex items-center justify-end gap-2">
                                <span className="h-2 rounded-full bg-brand-600" style={{ width: `${Math.max(4, (w.total / max) * 96)}px` }} aria-hidden="true" />
                                <span className="w-8 text-right">{w.total}</span>
                              </div>
                            </td>
                            <td className="py-2 pr-3 text-right">{w.completed}</td>
                            <td className="py-2 text-right">{w.noShow}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          </div>
        </>
      )}
    </>
  );
}
