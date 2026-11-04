// 구독/코인 탭 전환 바. 공용 SegmentedControl을 쓴다.
import AICoinIcon from '@/components/AICoinIcon';
import SegmentedControl from '@/components/ui/SegmentedControl';

interface PricingTabsProps {
  // 현재 선택된 탭 식별자
  tab: string;
  onTabChange: (tab: string) => void;
}

// SegmentedControl 형식의 탭 정의
const PRICING_TABS = [
  { id: 'subscription', label: '구독 요금제' },
  { id: 'coins', label: '생성권 패키지', icon: <AICoinIcon size={14} decorative /> },
];

export function PricingTabs({ tab, onTabChange }: PricingTabsProps) {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 24 }}>
      <SegmentedControl
        items={PRICING_TABS}
        value={tab}
        onChange={onTabChange}
      />
    </div>
  );
}
