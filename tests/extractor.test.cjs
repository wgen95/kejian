const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const script=fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/assets/tongji-extract.js'),'utf8');
let checks=0;
function run({raw=[],list=[],term=null,host='1.tongji.edu.cn',path='/studentTimeTable',component=true,calendarId=123}={}){
  const table={$options:{name:'StuLessonTimeTableCpt'},lessonTimeTableDatas:raw,tableData:list,queryParams:{calendarId},$children:[]};
  const calendar={$options:{name:'CalendarSelect'},preClickTerm:term,$children:[]};
  const root={$options:{name:'App'},$children:component?[table,calendar]:[]};root.$children.push(root); // actual walk must tolerate repeated/cyclic roots
  const ctx={location:{hostname:host,pathname:path},document:{getElementById:()=>({__vue__:root}),querySelectorAll:()=>[]},Date,Set,Array,Number,JSON,String};
  return JSON.parse(vm.runInNewContext(script,ctx,{timeout:1000}));
}
function test(label,fn){fn();checks++;console.log('PASS '+label);}
const list=[{courseCode:'A',courseName:'高等数学',teacherName:'张老师'},{courseCode:'B',courseName:'大学英语',teacherName:'李老师'}];
const a={courseCode:'A',dayOfWeek:1,timeStart:1,timeEnd:2,weeks:[1,3,5,7],value:'张老师  高等数学(01) [1-8单周] 四平-101'};
const b={courseCode:'B',dayOfWeek:1,timeStart:1,timeEnd:2,weeks:[2,4,6,8],value:'李老师  大学英语(02) [2-8双周] 嘉定-202'};
test('preserve irregular and alternating weeks',()=>{const r=run({raw:[a,b],list});assert.deepEqual(r.courses[0].weeks,[1,3,5,7]);assert.deepEqual(r.courses[1].weeks,[2,4,6,8]);assert.equal(r.courses[0].room,'四平-101');});
test('school merged display retains both raw records',()=>{const r=run({raw:[{...a,value:a.value+';'+b.value},{...b,remove:true,value:b.value+';'+a.value}],list});assert.equal(r.courses.length,2);assert.equal(r.courses[0].room,'四平-101');assert.equal(r.courses[1].name,'大学英语');assert.equal(r.courses[1].room,'嘉定-202');});
test('do not duplicate identical raw schedules',()=>assert.equal(run({raw:[a,a],list}).courses.length,1));
test('Sunday and numeric strings are parsed',()=>{const r=run({raw:[{...a,dayOfWeek:'7',timeStart:'11',timeEnd:'12',weeks:['1','8','16']}],list});assert.equal(r.courses[0].day,7);assert.deepEqual(r.courses[0].weeks,[1,8,16]);});
test('sort and deduplicate weeks',()=>assert.deepEqual(run({raw:[{...a,weeks:[8,1,8,3]}],list}).courses[0].weeks,[1,3,8]));
test('unrecognized week masks are rejected',()=>assert.match(run({raw:[{...a,weeks:15}],list}).error,/没有识别/));
test('missing names never become guessed courses',()=>assert.match(run({raw:[a],list:[]}).error,/没有识别/));
test('invalid time records do not discard other valid courses',()=>{const r=run({raw:[a,{...b,timeStart:2,timeEnd:1}],list});assert.equal(r.courses.length,1);assert.ok(r.warnings.length);});
test('malformed decimal week rejected',()=>assert.ok(run({raw:[{...a,weeks:[1.5]}],list}).error));
test('out of range weekday rejected',()=>assert.ok(run({raw:[{...a,dayOfWeek:0}],list}).error));
test('identity login origin cannot be extracted',()=>assert.match(run({host:'iam.tongji.edu.cn'}).error,/身份认证/));
test('lookalike hostname rejected',()=>assert.ok(run({host:'1.tongji.edu.cn.example.org'}).error));
test('wrong route rejected',()=>assert.match(run({path:'/login'}).error,/个人课表/));
test('unloaded component is actionable',()=>assert.match(run({component:false}).error,/尚未加载/));
test('unarranged course creates warning',()=>assert.ok(run({raw:[a],list}).warnings.some(x=>x.includes('大学英语'))));
test('matching school calendar metadata is copied',()=>{const r=run({raw:[a],list,term:{id:123,fullName:'2026-2027学年第1学期',beginDay:new Date(2026,8,9).getTime(),weekNum:20}});assert.equal(r.term.monday,'2026-09-07');assert.equal(r.term.totalWeeks,20);});
test('stale calendar is not attached',()=>assert.deepEqual(run({raw:[a],list,term:{id:999,fullName:'旧学期'}}).term,{}));
test('do not capture identity or account objects',()=>{const r=run({raw:[a],list:[{...list[0],studentCode:'secret-id',password:'secret-password',token:'secret-token'}]});assert.ok(!JSON.stringify(r).includes('secret'));});
test('read original nested schedules before display merges them',()=>{const r=run({raw:[{...a,value:'错误的合并显示',weeks:[1]}],list:[{...list[0],timeTableList:[{...a,teacherName:'甲老师',roomIdI18n:'101'},{...a,weeks:[2,4],teacherName:'乙老师',roomIdI18n:'202'}]}]});assert.equal(r.courses.length,2);assert.deepEqual(r.courses[0].weeks,[1,3,5,7]);assert.equal(r.courses[1].teacher,'乙老师');assert.equal(r.courses[1].room,'202');});
test('nested course name inherited if schedule omits it',()=>{const r=run({list:[{...list[0],timeTableList:[{dayOfWeek:2,timeStart:3,timeEnd:4,weeks:[1,2],roomIdI18n:'同济101'}]}]});assert.equal(r.courses[0].name,'高等数学');assert.equal(r.courses[0].day,2);});
test('personal schedule route is accepted',()=>assert.equal(run({path:'/StudentSchedule',raw:[a],list}).courses.length,1));
test('neutral import protocol version is present',()=>assert.equal(run({raw:[a],list}).format,'kejian-import/1'));
test('reject old calendar data while a new term is loading',()=>assert.match(run({raw:[{...a,calendarId:456}],list,calendarId:123}).error,/学期数据正在更新/));
test('matching record calendar is accepted',()=>assert.equal(run({raw:[{...a,calendarId:'123'}],list,calendarId:123}).courses.length,1));
console.log('Extractor tests passed: '+checks);
