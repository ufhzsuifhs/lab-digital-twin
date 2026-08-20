var p=Object.defineProperty;var f=(t,e,s)=>e in t?p(t,e,{enumerable:!0,configurable:!0,writable:!0,value:s}):t[e]=s;var i=(t,e,s)=>f(t,typeof e!="symbol"?e+"":e,s);import{h as o}from"./request-DKZ18tOi.js";function g(){return o.get("/dashboard/overview")}function v(){return o.get("/dashboard/devices")}function y(t){return o.get(`/dashboard/device/${t}`)}class S{constructor(){i(this,"ws",null);i(this,"handlers",new Map);i(this,"pendingSubs",[]);i(this,"connected",!1)}connect(e){return new Promise((s,r)=>{const n=new WebSocket(e);this.ws=n,n.onopen=()=>{this.connected=!0,n.send(`CONNECT
accept-version:1.1,1.0
heart-beat:10000,10000

\0`),this.pendingSubs.forEach(c=>this.sendSubscribe(c)),this.pendingSubs=[],s()},n.onerror=c=>r(c),n.onmessage=c=>this.handleFrame(String(c.data)),n.onclose=()=>{this.connected=!1}})}subscribe(e,s){this.handlers.has(e)||this.handlers.set(e,new Set),this.handlers.get(e).add(s),this.connected?this.sendSubscribe(e):this.pendingSubs.push(e)}sendSubscribe(e){var s;(s=this.ws)==null||s.send(`SUBSCRIBE
id:sub-${encodeURIComponent(e)}
destination:${e}

\0`)}handleFrame(e){const s=e.indexOf(`

`);if(s<0)return;const r=e.slice(0,s);let n=e.slice(s+2);n.endsWith("\0")&&(n=n.slice(0,-1));const c=/destination:([^\n]+)/.exec(r);if(!c)return;const a=c[1].trim(),h=this.handlers.get(a);if(h){const u={destination:a,body:n};h.forEach(b=>{try{b(u)}catch(l){console.error("stomp handler error",l)}})}}disconnect(){var e;this.connected=!1,(e=this.ws)==null||e.close(),this.ws=null}}const d=new S;function x(t){d.subscribe("/topic/device-status",e=>{try{t(JSON.parse(e.body))}catch{}})}function C(t){d.subscribe("/topic/experiment-status",e=>{try{t(JSON.parse(e.body))}catch{}})}export{C as a,v as b,x as c,y as d,g as f,d as s};
