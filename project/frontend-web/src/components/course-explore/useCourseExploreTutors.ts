// 튜터 목록을 불러오고 기본 선택값을 정한다
import { useEffect, useMemo, useState } from 'react';
import { getMyTutors } from '@/apis';
import type { TeacherItem } from './types';
import {
  createDefaultTutorCountError,
  createTutorLoadError,
  splitTutorItems,
} from './courseExploreTutors';

export function useCourseExploreTutors(): {
  teacher: string;
  defaultTutors: TeacherItem[];
  customTutors: TeacherItem[];
  teachers: TeacherItem[];
  tutorLoadError: string | null;
  setTeacher: (id: string) => void;
} {
  const [teacher, setTeacher] = useState('');
  const [defaultTutors, setDefaultTutors] = useState<TeacherItem[]>([]);
  const [customTutors, setCustomTutors] = useState<TeacherItem[]>([]);
  const [tutorLoadError, setTutorLoadError] = useState<string | null>(null);
  const teachers = useMemo(() => [...defaultTutors, ...customTutors], [defaultTutors, customTutors]);

  useEffect(() => {
    let mounted = true;
    void getMyTutors().then((res) => {
      if (!mounted) return;
      const { defaultTutors: defaults, customTutors: customs, allTutors } = splitTutorItems(res.data?.tutors ?? []);
      setDefaultTutors(defaults); setCustomTutors(customs);
      setTutorLoadError(createDefaultTutorCountError(defaults.length));
      const firstDefault = defaults[0];
      setTeacher(current => firstDefault && allTutors.some(t => t.id === current) ? current : firstDefault?.id ?? '');
    }).catch((error: unknown) => {
      if (!mounted) return;
      setDefaultTutors([]); setCustomTutors([]); setTeacher('');
      setTutorLoadError(createTutorLoadError(error));
    });

    return () => { mounted = false; };
  }, []);

  return { teacher, defaultTutors, customTutors, teachers, tutorLoadError, setTeacher };
}
