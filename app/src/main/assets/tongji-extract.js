(function () {
  'use strict';
  // Read only named timetable/calendar fields. Never inspect cookies, storage or login inputs.
  var host = location.hostname.toLowerCase();
  if (host !== '1.tongji.edu.cn') return JSON.stringify({ error: '请先完成统一身份认证，返回同济课表页面。' });
  if (!/^\/(?:studentTimeTable|StudentSchedule|GraduateStudentTimeTable)\/?$/.test(location.pathname)) return JSON.stringify({ error: '请打开个人课表页面，再点击导课。' });
  var root = document.getElementById('app'), queue = [], seen = [], timetable = null, calendars = [];
  if (root && root.__vue__) queue.push(root.__vue__);
  document.querySelectorAll('.edu-page-warp, .classTimeTable').forEach(function (el) { if (el.__vue__) queue.push(el.__vue__); });
  while (queue.length && seen.length < 5000) {
    var vm = queue.shift();
    if (!vm || seen.indexOf(vm) >= 0) continue;
    seen.push(vm);
    var name = vm.$options && vm.$options.name;
    if (name === 'StuLessonTimeTableCpt' && Array.isArray(vm.lessonTimeTableDatas)) timetable = vm;
    if (name === 'CalendarSelect' && vm.preClickTerm) calendars.push(vm.preClickTerm);
    (vm.$children || []).forEach(function (c) { queue.push(c); });
  }
  if (!timetable) return JSON.stringify({ error: '课表组件尚未加载。请进入课表页面、选择学期并点击筛选，然后重试。' });
  var list = Array.isArray(timetable.tableData) ? timetable.tableData : [], raw = [], courses = [], warnings = [], keys = {};
  // The course list keeps each scheduled teacher/room record before the visual grid merges overlaps.
  list.forEach(function(c){if(Array.isArray(c.timeTableList))c.timeTableList.forEach(function(r){raw.push(Object.assign({courseName:c.courseName,courseCode:c.courseCode,teacherName:c.teacherName},r));});});
  if(!raw.length) raw = timetable.lessonTimeTableDatas;
  var termId = timetable.queryParams && timetable.queryParams.calendarId;
  if(raw.some(function(r){return r.calendarId!=null&&termId!=null&&String(r.calendarId)!==String(termId);})){return JSON.stringify({error:'学期数据正在更新，请等筛选完成后再导课。'});}
  if (!raw.length) return JSON.stringify({ error: '当前学期没有已排课数据，或页面还在加载。请核对学期后重试。' });
  function clean(s) { return String(s == null ? '' : s).replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim(); }
  function num(n) { return typeof n === 'number' && Number.isInteger(n) ? n : typeof n === 'string' && /^\d+$/.test(n) ? Number(n) : NaN; }
  raw.forEach(function (r, i) {
    var day = num(r.dayOfWeek), start = num(r.timeStart), end = num(r.timeEnd);
    if (!Array.isArray(r.weeks)) { warnings.push('第 ' + (i + 1) + ' 条排课周次格式未知，未导入。'); return; }
    var weeks = r.weeks.map(num);
    if (!weeks.length || weeks.some(function (w) { return !Number.isInteger(w) || w < 1 || w > 53; }) || day < 1 || day > 7 || !Number.isInteger(day) || start < 1 || end < start || end > 16 || !Number.isInteger(start) || !Number.isInteger(end)) {
      warnings.push('第 ' + (i + 1) + ' 条排课时间无效，未导入。'); return;
    }
    weeks = Array.from(new Set(weeks)).sort(function(a,b){return a-b;});
    var candidates = list.filter(function (c) { return r.courseCode != null && [c.courseCode, c.newCourseCode].some(function (code) { return code != null && String(code) === String(r.courseCode); }); });
    var detail = candidates[0] || {}, name = clean(r.courseName || detail.courseName), value = clean(r.value || r.label || r.timeTab);
    var segments = value.split(/[;；]/), segment = segments.find(function (s) { return name && s.indexOf(name) >= 0; }) || segments[0] || '';
    if (!name) { warnings.push('第 ' + (i + 1) + ' 条排课无法匹配课程名称，未导入。'); return; }
    var room = clean(r.roomIdI18n || r.roomLable || r.classRoomI18n || detail.classRoomI18n || detail.classRoomPractice);
    if (!room && segment.indexOf(']') >= 0) room = segment.substring(segment.lastIndexOf(']') + 1).trim();
    if (room === 'undefined' || room === 'null') room = '';
    var teacher = clean(r.teacherName || detail.teacherName);
    var note = segment;
    var key = [name, room, teacher, day, start, end, weeks.join(',')].join('|');
    if (keys[key]) return;
    keys[key] = true;
    courses.push({ name: name, room: room, teacher: teacher, day: day, start: start, end: end, weeks: weeks, color: courses.length % 6, note: note });
    if (candidates.length > 1) warnings.push(name + '：课程代码匹配到多个教学班，请核对教师和地点。');
  });
  list.forEach(function (c) { if (!courses.some(function (r) { return r.name === clean(c.courseName); })) warnings.push(clean(c.courseName) + '：没有可识别的排课，请按学院通知安排。'); });
  if (!courses.length) return JSON.stringify({ error: '没有识别到可导入课程。页面格式可能已变化。', warnings: warnings });
  var term = calendars.find(function (c) { return String(c.id) === String(termId); }), meta = {};
  if (term) {
    meta.name = clean(term.fullName);
    var d = new Date(term.beginDay);
    if (term.beginDay != null && !isNaN(d.getTime())) {
      // Display week 1 from the Monday containing the school calendar's beginDay.
      d.setDate(d.getDate() - (d.getDay() + 6) % 7);
      meta.monday = d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
    }
    var count = num(term.weekNum);
    if (count >= 1 && count <= 53) meta.totalWeeks = count;
  }
  return JSON.stringify({ format: 'kejian-import/1', courses: courses, term: meta, warnings: Array.from(new Set(warnings)), source: 'tongji-page-v2', rawCount: raw.length });
})()
