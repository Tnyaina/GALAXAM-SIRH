import { TrendingUp, TrendingDown } from 'lucide-react';

export default function StatCard({ title, value, change, changeLabel, icon, color = 'blue' }) {
  const isPositive = change >= 0;

  const colors = {
    blue: 'bg-blue-50 text-[#2563EB]',
    green: 'bg-emerald-50 text-emerald-600',
    orange: 'bg-orange-50 text-orange-600',
    purple: 'bg-purple-50 text-purple-600',
    red: 'bg-rose-50 text-rose-600',
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-6 card-hover">
      <div className="flex items-start justify-between mb-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-widest text-slate-400">{title}</p>
          <p className="text-3xl font-bold text-slate-900 mt-1" style={{fontFamily: 'Syne, sans-serif'}}>
            {value}
          </p>
        </div>
        {icon && (
          <div className={`w-11 h-11 rounded-xl ${colors[color]} flex items-center justify-center`}>
            {icon}
          </div>
        )}
      </div>

      {change !== undefined && (
        <div className={`flex items-center gap-1.5 text-xs font-semibold ${isPositive ? 'text-emerald-600' : 'text-red-500'}`}>
          {isPositive ? <TrendingUp size={13} /> : <TrendingDown size={13} />}
          <span>{isPositive ? '+' : ''}{change}%</span>
          {changeLabel && <span className="text-slate-400 font-normal">{changeLabel}</span>}
        </div>
      )}
    </div>
  );
}
