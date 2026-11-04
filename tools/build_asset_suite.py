#!/usr/bin/env python3
"""Builds PackFlow's Asset Generator (Sprites + Tiles tabs) from the Imperium asset suite.

Input:  imported/imperium/imperium_asset_suite_v22.html (kept unmodified)
Output: app/src/main/assets/asset-suite/index.html

Changes:
  * Renamed to "Asset Generator".
  * Image model list updated (gemini-3.1-flash-image is deprecated).
  * Inside PackFlow the Gemini key comes from / goes to Android (encrypted with the Android
    Keystore) through window.PackFlowKeys; it is never written to localStorage or baked into
    a file ("Save key into a copy" is hidden).
  * Saves and downloads also go into the current PackFlow project (sprites/, tiles/)
    through window.PackFlowProject.
  * #tab=gen / #tab=tiles in the URL opens that tab.
  * Fix for sprite colors drifting toward blue:
      1. neutralize(): measures the tint of low-chroma pixels (Lab a*/b*) and pulls it to
         the tone anchor's tint (or the palette's neutrals). Generated images came back
         slightly blue and each re-generation from the last sprite compounded it;
         saturated colors are left alone;
      2. snapPalette(): hue-aware distance (lightness + chroma + 2.5 x hue), so warm and
         neutral colors no longer snap to the steel-blue palette entries;
      3. toneMatch(): scales a pixel without clipping single channels, which shifted hue.
Run: python3 tools/build_asset_suite.py
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "imported/imperium/imperium_asset_suite_v22.html")
OUT = os.path.join(ROOT, "app/src/main/assets/asset-suite/index.html")

html = open(SRC, encoding="utf-8").read()


def patch(old, new, count=1):
    global html
    n = html.count(old)
    if n != count:
        sys.exit(f"Patch anchor found {n} times (expected {count}): {old[:80]!r}")
    html = html.replace(old, new)


# --- name ---
patch("<title>Imperium RTS: Asset Suite", "<title>Asset Generator")
patch("<h2>Imperium Asset Suite</h2>", "<h2>Asset Generator</h2>")

# --- image models ---
patch('''      <option value="gemini-3.1-flash-image">Gemini 3.1 Flash Image (fast)</option>
      <option value="gemini-3-pro-image">Gemini 3 Pro Image (best quality)</option>''',
      '''      <option value="gemini-nano-banana-2.1">Nano Banana 2.1 (fast)</option>
      <option value="gemini-3-pro-image">Gemini 3 Pro Image (best quality)</option>
      <option value="gemini-3.1-flash-image">Gemini 3.1 Flash Image (deprecated)</option>''')
patch("const MODELS=['gemini-3.1-flash-image','gemini-3-pro-image'];",
      "const MODELS=['gemini-nano-banana-2.1','gemini-3-pro-image','gemini-3.1-flash-image'];")
patch("if(!cfg.model||cfg.model==='gemini-2.5-flash-image')cfg.model=MODELS[0]; // 2.5 was retired by Google",
      "if(!cfg.model||/^gemini-2\\.5-flash-image|-image-preview$/.test(cfg.model))cfg.model=MODELS[0]; // retired by Google")

# --- key handling inside PackFlow ---
patch("if(!cfg.key&&BAKED_KEY)cfg.key=BAKED_KEY;",
      """if(!cfg.key&&BAKED_KEY)cfg.key=BAKED_KEY;
// PackFlow: the key lives in Android (Keystore-encrypted), never in localStorage.
const PF_KEYS=window.PackFlowKeys||null,PF_PROJECT=window.PackFlowProject||null;
if(PF_KEYS){try{cfg.key=PF_KEYS.geminiKey()||''}catch(e){cfg.key=''}}
const saveCfg=()=>{if(PF_KEYS){try{PF_KEYS.setGeminiKey(cfg.key||'')}catch(e){}ls.set('imp:cfg',{...cfg,key:''})}else ls.set('imp:cfg',{...cfg})};""")
patch("ls.set('imp:cfg',cfg)", "saveCfg()", count=html.count("ls.set('imp:cfg',cfg)"))
patch("$('bakeKey').onclick=async()=>{",
      """if(PF_KEYS){$('bakeKey').hidden=true;$('keyState').textContent='Stored encrypted on this phone by PackFlow (Android Keystore). Music and Voice use the same key.'}
$('bakeKey').onclick=async()=>{if(PF_KEYS)return;""")

# --- downloads go into the PackFlow project ---
patch("const dl=(b,n)=>{const a=document.createElement('a');a.href=URL.createObjectURL(b);a.download=n;a.click()};",
      """const b64Of=b=>new Promise((res,rej)=>{const r=new FileReader();r.onload=()=>res(String(r.result).split(',')[1]||'');r.onerror=()=>rej(r.error);r.readAsDataURL(b)});
const pfSave=async(path,blob)=>{if(!window.PackFlowProject)return null;try{return window.PackFlowProject.saveFile(path,await b64Of(blob))}catch(e){return null}};
const dl=(b,n)=>{if(window.PackFlowProject){const folder=/\\.zip$/i.test(n)?'builds':/atlas|tile/i.test(n)?'tiles/downloads':'sprites/downloads';
  pfSave(folder+'/'+n,b).then(p=>msg(p?'Saved to the PackFlow project: '+p:'Could not save '+n+' to the project.'));return}
  const a=document.createElement('a');a.href=URL.createObjectURL(b);a.download=n;a.click()};""")

# --- saves mirror into project folders ---
patch("""  for(const f of files){const rec={project:cur,faction:fac,sub,type,name,file:b+'.'+f.ext,blob:f.blob,t:Date.now()};await putAsset({...rec,id:idOf(rec)})}
  P().unexp=(P().unexp||0)+files.length;saveProj();updateBadge();""",
      """  for(const f of files){const rec={project:cur,faction:fac,sub,type,name,file:b+'.'+f.ext,blob:f.blob,t:Date.now()};await putAsset({...rec,id:idOf(rec)})}
  if(window.PackFlowProject){ // also write into the PackFlow project: sprites/<faction>/<subfaction>/<asset>/ or tiles/<faction>/
    const dir=type==='Tilesets'?`tiles/${slug(fac)}`:type==='References'?'sprites/references':`sprites/${slug(fac)}/${slug(sub)}/${slug(name)}`;
    for(const f of files)await pfSave(`${dir}/${b}.${f.ext}`,f.blob);
  }else{P().unexp=(P().unexp||0)+files.length}
  saveProj();updateBadge();""")

# --- blue drift fixes ---
patch("""const lum=(r,g,b)=>.2126*r+.7152*g+.0722*b;""",
      """const lum=(r,g,b)=>.2126*r+.7152*g+.0722*b;
const labToRgb=(L,A,B)=>{const fy=(L+16)/116,fx=fy+A/500,fz=fy-B/200,inv=t=>t**3>.008856?t**3:(t-16/116)/7.787,
  X=inv(fx)*.95047,Y=inv(fy),Z=inv(fz)*1.08883,lin=[3.2406*X-1.5372*Y-.4986*Z,-.9689*X+1.8758*Y+.0415*Z,.0557*X-.204*Y+1.057*Z];
  return lin.map(v=>{v=Math.max(0,Math.min(1,v));return Math.round(255*(v<=.0031308?12.92*v:1.055*v**(1/2.4)-.055))})};
function castOf(c){ // average Lab tint (a*, b*) of the low-chroma pixels: the "cast" of an image
  const d=c.getContext('2d').getImageData(0,0,c.width,c.height).data;let A=0,B=0,n=0,op=0;
  for(let i=0;i<d.length;i+=4){if(d[i+3]<128)continue;op++;const l=toLab(d[i],d[i+1],d[i+2]);if(l[0]<8||l[0]>97||Math.hypot(l[1],l[2])>18)continue;A+=l[1];B+=l[2];n++}
  return n>=Math.max(12,op*.03)?[A/n,B/n]:null}
function paletteCast(hexList){const v=(hexList||'').split(',').map(s=>s.trim()).filter(s=>/^#[0-9a-f]{6}$/i.test(s))
  .map(h=>toLab(...[1,3,5].map(i=>parseInt(h.substr(i,2),16)))).filter(l=>Math.hypot(l[1],l[2])<=18);
  return v.length?[v.reduce((s,l)=>s+l[1],0)/v.length,v.reduce((s,l)=>s+l[2],0)/v.length]:[0,0]}
function neutralize(c){ // fix the drift toward blue: pull the image's neutral tint to the target (tone anchor, else the palette's neutrals)
  const cur=castOf(c);if(!cur)return null;
  const tgt=(P().cast)||paletteCast(PAL());let dA=tgt[0]-cur[0],dB=tgt[1]-cur[1];const m=Math.hypot(dA,dB);
  if(m<1.5)return null;if(m>15){dA*=15/m;dB*=15/m}
  const x=c.getContext('2d'),d=x.getImageData(0,0,c.width,c.height),a=d.data,memo=new Map();
  for(let i=0;i<a.length;i+=4){if(!a[i+3])continue;const key=a[i]<<16|a[i+1]<<8|a[i+2];let o=memo.get(key);
    if(!o){const l=toLab(a[i],a[i+1],a[i+2]),ch=Math.hypot(l[1],l[2]),w=ch<=25?1:ch>=50?0:(50-ch)/25; // saturated colors (team colors, gold) stay put
      o=w?labToRgb(l[0],l[1]+dA*w,l[2]+dB*w):[a[i],a[i+1],a[i+2]];memo.set(key,o)}
    a[i]=o[0];a[i+1]=o[1];a[i+2]=o[2]}
  x.putImageData(d,0,0);return[+dA.toFixed(2),+dB.toFixed(2)];
}""")
patch("const full=mk(img.width,img.height);full.getContext('2d').drawImage(img,0,0);keyOut(full,tol);",
      "const full=mk(img.width,img.height);full.getContext('2d').drawImage(img,0,0);keyOut(full,tol);if(!$('wb')||$('wb').checked)neutralize(full);")
patch("  const t=downMedian(cell,b.l,b.t,cw,ch,w,h);\n  toneMatch(t,P().tone);snapPalette(t,PAL());",
      "  const t=downMedian(cell,b.l,b.t,cw,ch,w,h);\n  if(!$('wb')||$('wb').checked)neutralize(t);toneMatch(t,P().tone);snapPalette(t,PAL());")
patch("pal.forEach((p,k)=>{const e=1.6*(p.l[0]-l[0])**2+(p.l[1]-l[1])**2+(p.l[2]-l[2])**2;if(e<bd){bd=e;b=k}});",
      "pal.forEach((p,k)=>{const dc=Math.hypot(l[1],l[2])-Math.hypot(p.l[1],p.l[2]),dab=(p.l[1]-l[1])**2+(p.l[2]-l[2])**2,dh=Math.max(0,dab-dc*dc),e=1.6*(p.l[0]-l[0])**2+dc*dc+2.5*dh;if(e<bd){bd=e;b=k}});")
patch("const l=lum(a[i],a[i+1],a[i+2])||1,k=Math.min(3,map[Math.round(l)]/l);",
      "const l=lum(a[i],a[i+1],a[i+2])||1,k=Math.min(3,map[Math.round(l)]/l,255/(Math.max(a[i],a[i+1],a[i+2])||1));")
patch('''<label style="display:inline-block;margin-left:10px;color:var(--text)"><input type="checkbox" id="outline" checked>Outline</label>''',
      '''<label style="display:inline-block;margin-left:10px;color:var(--text)"><input type="checkbox" id="outline" checked>Outline</label>
    <label style="display:inline-block;margin-left:10px;color:var(--text)"><input type="checkbox" id="wb" checked>Neutral colors (fix blue tint)</label>''')
patch("$('px').onchange=$('tol').onchange=$('pop').onchange=$('outline').onchange=$('ncol').onchange=render;",
      "$('px').onchange=$('tol').onchange=$('pop').onchange=$('outline').onchange=$('ncol').onchange=$('wb').onchange=render;")

patch("$('setAnchor').onclick=()=>{if(!curC)return msg('Generate or pick an asset first.');P().tone=lumCdf(curC);",
      "$('setAnchor').onclick=()=>{if(!curC)return msg('Generate or pick an asset first.');P().tone=lumCdf(curC);P().cast=castOf(curC);")

# --- open a tab from the URL (#tab=gen or #tab=tiles) ---
last = html.rindex("</script>")
html = html[:last] + """
(()=>{const t=new URLSearchParams(location.hash.slice(1)).get('tab');if(t&&TITLES[t])switchTab(t)})();
""" + html[last:]

if "AIza" in html:
    sys.exit("Refusing to write: output looks like it contains an API key")
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w", encoding="utf-8").write(html)
print("wrote", os.path.relpath(OUT, ROOT), len(html), "bytes")
