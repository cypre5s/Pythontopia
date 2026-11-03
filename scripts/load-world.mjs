// Authenticated world reads only: does not load-test AI or Judge0.
// REBORN_LOAD_TOKEN and REBORN_LOAD_CLASSROOM must be supplied explicitly.
const token=process.env.REBORN_LOAD_TOKEN;
const classroom=process.env.REBORN_LOAD_CLASSROOM;
if(!token||!classroom)throw new Error('Set REBORN_LOAD_TOKEN and REBORN_LOAD_CLASSROOM');
const base=process.env.REBORN_LOAD_URL??'http://127.0.0.1:18081';
const total=Math.min(1000,Math.max(1,Number(process.env.REBORN_LOAD_REQUESTS??200)));
const concurrency=Math.min(50,Math.max(1,Number(process.env.REBORN_LOAD_CONCURRENCY??10)));
const times=[];let next=0,failed=0;const start=performance.now();
await Promise.all(Array.from({length:concurrency},async()=>{while(next++<total){const t=performance.now();try{const res=await fetch(`${base}/api/world?classroom_id=${encodeURIComponent(classroom)}`,{headers:{Authorization:`Bearer ${token}`},signal:AbortSignal.timeout(10000)});await res.arrayBuffer();if(!res.ok)failed++;}catch{failed++;}times.push(performance.now()-t);}}));
times.sort((a,b)=>a-b);
console.log(JSON.stringify({scope:'authenticated world reads; one account, no AI/judge',requests:times.length,concurrency,failed,p50_ms:Math.round(times[Math.floor(times.length*.5)]),p95_ms:Math.round(times[Math.min(times.length-1,Math.floor(times.length*.95))]),elapsed_ms:Math.round(performance.now()-start)},null,2));
if(failed)process.exitCode=1;
