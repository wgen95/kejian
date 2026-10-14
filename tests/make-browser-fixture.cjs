const fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'..');
const extractor=fs.readFileSync(path.join(root,'app/src/main/assets/tongji-extract.js'),'utf8');
const vm={ $options:{name:'StuLessonTimeTableCpt'},queryParams:{calendarId:123},lessonTimeTableDatas:[],tableData:[
 {courseCode:'A',courseName:'高等数学',teacherName:'甲老师',timeTableList:[{dayOfWeek:1,timeStart:1,timeEnd:2,weeks:[1,3,5,7],roomIdI18n:'教学楼101'}]},
 {courseCode:'B',courseName:'大学英语',teacherName:'乙老师',timeTableList:[{dayOfWeek:3,timeStart:3,timeEnd:4,weeks:[2,4,6,8],roomIdI18n:'教学楼202'}]}
],$children:[]};
const term={$options:{name:'CalendarSelect'},preClickTerm:{id:123,fullName:'2026-2027学年第1学期',beginDay:new Date(2026,8,7).getTime(),weekNum:20},$children:[]};
const html='<!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>我的课表 · 合成测试页面</title><body style="font-family:sans-serif;padding:20px;background:#eee"><div id="app"><h1>我的课表</h1><p>这是验证读取器的合成数据，不是真实账号课表。</p><button id="read">导课（验证读取脚本）</button><pre id="result" style="white-space:pre-wrap"></pre></div><script>document.getElementById("app").__vue__={$options:{name:"App"},$children:['+JSON.stringify(vm)+','+JSON.stringify(term)+']};document.getElementById("read").onclick=function(){var result='+extractor+';window.__testCallback=JSON.stringify(result);document.getElementById("result").textContent=result;};</script></body></html>';
fs.mkdirSync(path.join(root,'build/browser-test'),{recursive:true});fs.writeFileSync(path.join(root,'build/browser-test/fixture.html'),html);
fs.writeFileSync(path.join(root,'build/browser-test/open-fixture.cjs'),'async page => { const html = '+JSON.stringify(html)+'; await page.route("https://1.tongji.edu.cn/**", route => route.fulfill({contentType:"text/html",body:html})); await page.goto("https://1.tongji.edu.cn/studentTimeTable"); }');
console.log('Synthetic browser fixture ready');
