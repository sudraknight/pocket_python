'use strict';
const $ = id => document.getElementById(id);
const native = window.Native;
let current = '', loading = false, dirty = false, running = false, saveTimer, toastTimer, dialogMode = 'create';
let outputStarted = false, outputLength = 0;
const builtins = ('abs all any ascii bin bool breakpoint bytearray bytes callable chr classmethod compile complex delattr dict dir divmod enumerate eval exec filter float format frozenset getattr globals hasattr hash help hex id input int isinstance issubclass iter len list locals map max memoryview min next object oct open ord pow print property range repr reversed round set setattr slice sorted staticmethod str sum super tuple type vars zip __import__ True False None and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case math random datetime json re pathlib statistics sqlite3 asyncio numpy requests sympy PIL').split(' ');
function hint(cm) {
  const cur = cm.getCursor(), token = cm.getTokenAt(cur), match = token.string.match(/[\w]*$/), prefix = match ? match[0] : '';
  const words = new Set([...builtins, ...(cm.getValue().match(/\b[A-Za-z_]\w*\b/g)||[])]);
  return {list:[...words].filter(x=>x.startsWith(prefix)&&x!==prefix).sort().slice(0,60), from:CodeMirror.Pos(cur.line,cur.ch-prefix.length), to:cur};
}
const editor = CodeMirror.fromTextArea($('code'), {
  mode:{name:'python',version:3,singleLineStringErrors:false}, lineNumbers:true, indentUnit:4, tabSize:4,
  indentWithTabs:false, matchBrackets:true, autoCloseBrackets:true, styleActiveLine:true,
  inputStyle:'contenteditable', spellcheck:false, autocorrect:false, autocapitalize:false, lineWrapping:false,
  extraKeys:{'Ctrl-S':()=>saveCurrent(true),'Cmd-S':()=>saveCurrent(true),'Ctrl-Enter':runCode,
    'Ctrl-Space':cm=>cm.showHint({hint,completeSingle:false}),
    'Tab':cm=>cm.somethingSelected()?cm.indentSelection('add'):cm.replaceSelection(' '.repeat(4-(cm.getCursor().ch%4)),'end'),
    'Shift-Tab':cm=>cm.indentSelection('subtract')}
});
let fontSize = Number(localStorage.getItem('fontSize')) || 13;
editor.getWrapperElement().style.fontSize=fontSize+'px';
function result(raw) { const value=JSON.parse(raw); if(!value.ok) throw Error(value.error||'Operation failed'); return value; }
function toast(message) { $('toast').textContent=message; $('toast').hidden=false; clearTimeout(toastTimer); toastTimer=setTimeout(()=>$('toast').hidden=true,3500); }
function updateLabels() {
  $('fileName').textContent=current||'No file open'; $('crumb').textContent=current||'Create a file';
  $('dirtyDot').classList.toggle('dirty',dirty); $('dirtyDot').setAttribute('aria-label',dirty?'Unsaved changes':'Saved');
  $('emptyState').hidden=!!current; $('runButton').disabled=!current&&!running;
  $('saveButton').disabled=!current;
}
window.saveCurrent = function(show=false) {
  clearTimeout(saveTimer);
  if(!current) return true;
  try { result(native.save(current,editor.getValue())); dirty=false; updateLabels(); if(show) toast('Saved '+current); return true; }
  catch(error) { toast('Save failed: '+error.message); return false; }
};
editor.on('change',()=>{
  if(loading||!current)return;
  dirty=true; updateLabels(); clearTimeout(saveTimer); saveTimer=setTimeout(()=>saveCurrent(),550);
});
editor.on('cursorActivity',()=>{const pos=editor.getCursor();$('cursorStatus').textContent=`Ln ${pos.line+1}, Col ${pos.ch+1}`;});
function openFile(name) {
  if(!saveCurrent())return false;
  try {
    const value=result(native.read(name));
    current=name; loading=true; editor.setValue(value.text); editor.clearHistory(); loading=false; dirty=false;
    localStorage.setItem('activeFile',name); updateLabels(); renderFiles(); closePanels(); editor.refresh(); return true;
  } catch(error) { loading=false; toast(error.message); return false; }
}
function renderFiles() {
  const files=JSON.parse(native.list()); $('fileCount').textContent=files.length;
  $('fileList').replaceChildren();
  files.forEach(name=>{
    const button=document.createElement('button'); button.className='file-item'+(name===current?' selected':'');
    const mark=document.createElement('span');mark.className='py-mark';mark.textContent='Py';
    const label=document.createElement('span');label.textContent=name;button.append(mark,label);
    button.onclick=()=>openFile(name);$('fileList').append(button);
  }); return files;
}
function closePanels(){ $('drawer').hidden=true;$('menu').hidden=true;$('shade').hidden=true; }
function showDrawer(){closePanels();renderFiles();$('drawer').hidden=false;$('shade').hidden=false;}
function fileDialog(mode) {
  if(!saveCurrent())return;closePanels();dialogMode=mode;
  $('dialogTitle').textContent=mode==='rename'?'Rename Python file':'New Python file';
  $('dialogSubmit').textContent=mode==='rename'?'Rename file':'Create file';
  $('filenameInput').value=mode==='rename'?current:'';$('filenameError').textContent='';
  $('fileDialog').showModal();setTimeout(()=>$('filenameInput').focus(),80);
}
$('fileForm').onsubmit=event=>{
  event.preventDefault();let name=$('filenameInput').value.trim();if(!name.endsWith('.py'))name+='.py';
  if(!/^[A-Za-z0-9_][A-Za-z0-9_. -]{0,95}\.py$/.test(name)){ $('filenameError').textContent='Use letters, numbers, spaces, underscores or hyphens (for example main.py).';return; }
  try {
    if(dialogMode==='rename') { result(native.rename(current,name));current=name; }
    else result(native.create(name));
    $('fileDialog').close();openFile(name);toast(dialogMode==='rename'?'File renamed':'Created '+name);
  } catch(error){$('filenameError').textContent=error.message;}
};
function appendOutput(text, kind='stdout') {
  if(!outputStarted){$('terminalBody').replaceChildren();outputStarted=true;}
  const body=$('terminalBody'), nearEnd=body.scrollHeight-body.scrollTop-body.clientHeight<80;
  if(outputLength>270000)return;
  outputLength+=text.length;
  const cls='output-'+kind,last=body.lastElementChild;
  if(last&&last.className===cls)last.append(document.createTextNode(text));
  else {const span=document.createElement('span');span.className=cls;span.textContent=text;body.append(span);}
  if(nearEnd)body.scrollTop=body.scrollHeight;
}
function setRunning(value,status) {
  running=value;$('runButton').classList.toggle('running',value);$('runLabel').textContent=value?'Stop':'Run';
  $('runButton').setAttribute('aria-label',value?'Stop Python program':'Run Python program');
  $('runIcon').firstElementChild.setAttribute('d',value?'M6 6h12v12H6z':'m8 5 11 7-11 7z');
  $('statusText').textContent=status|| (value?'Running':'Ready');$('statusDot').style.background=value?'#e0b285':'#8dbea5';
  $('terminalBadge').textContent=value?'Running':'Python';if(!value){$('inputForm').hidden=true;renderFiles();}
}
function runCode() {
  if(running){native.stop();return;}
  if(!current||!saveCurrent())return;
  $('terminalPanel').classList.remove('collapsed');$('collapseTerminal').textContent='⌄';
  $('terminalBody').replaceChildren();outputStarted=true;outputLength=0;
  appendOutput('› python '+current+'\n\n','meta');setRunning(true);
  editor.getInputField().blur();$('runButton').focus();
  native.run(current,editor.getValue());
}
window.onNativeEvent=function(type,data) {
  if(type==='output')appendOutput(data.text,data.kind);
  else if(type==='waiting') {
    $('inputForm').hidden=false;$('stdin').value='';$('statusText').textContent='Waiting for input';
    $('terminalBadge').textContent='Input';$('stdin').focus();
  } else if(type==='done') {
    appendOutput(`\n\n[Finished · exit ${data.code} · ${data.seconds.toFixed(2)}s]\n`,data.code?'stderr':'meta');setRunning(false,data.code?'Error':'Finished');
  } else if(type==='stopped') {appendOutput('\n[Stopped]\n','meta');setRunning(false,'Stopped');}
  else if(type==='crashed') {appendOutput('\n'+data.text+'\n','stderr');setRunning(false,'Error');}
  else if(type==='imported') {openFile(data.name);toast('Imported '+data.name);}
  else if(type==='notice')toast(data.text);
};
$('inputForm').onsubmit=event=>{event.preventDefault();let text=$('stdin').value;appendOutput(text+'\n','input');$('inputForm').hidden=true;$('stdin').blur();$('statusText').textContent='Running';$('terminalBadge').textContent='Running';native.input(text);};
$('eofButton').onclick=()=>{$('inputForm').hidden=true;native.eof();};
$('runButton').onclick=runCode;$('saveButton').onclick=()=>saveCurrent(true);
['newButton','drawerNew','emptyNew'].forEach(id=>$(id).onclick=()=>fileDialog('create'));
$('filesButton').onclick=showDrawer;$('closeDrawer').onclick=closePanels;$('shade').onclick=closePanels;
$('menuButton').onclick=()=>{const open=$('menu').hidden;closePanels();$('menu').hidden=!open;$('shade').hidden=!open;};
$('cancelDialog').onclick=()=>$('fileDialog').close();
$('importButton').onclick=()=>{if(saveCurrent()){closePanels();native.importFile();}};
$('exportButton').onclick=()=>{closePanels();if(current&&saveCurrent())native.exportFile(current,editor.getValue());};
$('renameButton').onclick=()=>{if(current)fileDialog('rename');};
$('deleteButton').onclick=()=>{closePanels();if(!current||!saveCurrent())return;$('deleteName').textContent=current;$('confirmDialog').showModal();};
$('cancelDelete').onclick=()=>$('confirmDialog').close();
$('confirmDelete').onclick=()=>{
  try {
    clearTimeout(saveTimer);result(native.delete(current));current='';dirty=false;loading=true;editor.setValue('');loading=false;
    localStorage.removeItem('activeFile');$('confirmDialog').close();const files=renderFiles();if(files.length)openFile(files[0]);updateLabels();toast('File deleted');
  }catch(error){toast(error.message);}
};
$('findButton').onclick=()=>{closePanels();editor.execCommand('findPersistent');editor.focus();};
$('undoButton').onclick=()=>{closePanels();editor.undo();};$('redoButton').onclick=()=>{closePanels();editor.redo();};
$('aboutButton').onclick=()=>{closePanels();$('aboutDialog').showModal();};$('closeAbout').onclick=()=>$('aboutDialog').close();
$('copyOutput').onclick=()=>{native.copy($('terminalBody').textContent);toast('Terminal copied');};
$('clearOutput').onclick=()=>{$('terminalBody').replaceChildren();outputStarted=true;outputLength=0;};
$('collapseTerminal').onclick=()=>{const collapsed=$('terminalPanel').classList.toggle('collapsed');$('collapseTerminal').textContent=collapsed?'⌃':'⌄';$('collapseTerminal').setAttribute('aria-label',collapsed?'Expand terminal':'Collapse terminal');editor.refresh();};
$('fontButton').onclick=()=>{fontSize=fontSize>=18?11:fontSize+1;editor.getWrapperElement().style.fontSize=fontSize+'px';localStorage.setItem('fontSize',fontSize);editor.refresh();toast('Editor text: '+fontSize+' px');};
document.querySelectorAll('[data-key]').forEach(button=>{
  button.addEventListener('pointerdown',event=>event.preventDefault());
  button.onclick=()=>{
    const key=button.dataset.key;editor.focus();
    if(key==='tab')editor.somethingSelected()?editor.indentSelection('add'):editor.replaceSelection('    ','end');
    else if(key==='unindent')editor.indentSelection('subtract');
    else if(key==='left'||key==='right')editor.execCommand(key==='left'?'goCharLeft':'goCharRight');
    else if(key==='hint')editor.showHint({hint,completeSingle:false});
    else if('([{\''.includes(key)){const close={'(':')','[':']','{':'}',"'":"'"}[key];const selection=editor.getSelection();editor.replaceSelection(key+selection+close,'end');if(!selection)editor.execCommand('goCharLeft');}
    else editor.replaceSelection(key,'end');
  };
});
let dragging=false;
function resizeTerminal(y){const main=document.querySelector('main').getBoundingClientRect();const height=Math.min(main.height-150,Math.max(105,main.bottom-y));document.documentElement.style.setProperty('--terminal',height+'px');$('terminalPanel').classList.remove('collapsed');editor.refresh();}
$('splitter').onpointerdown=event=>{dragging=true;$('splitter').setPointerCapture(event.pointerId);};
$('splitter').onpointermove=event=>{if(dragging)resizeTerminal(event.clientY);};
$('splitter').onpointerup=()=>dragging=false;
$('splitter').onkeydown=event=>{if(event.key==='ArrowUp'||event.key==='ArrowDown'){event.preventDefault();resizeTerminal($('splitter').getBoundingClientRect().top+(event.key==='ArrowUp'?-25:25));}};
window.handleBack=()=>{for(const id of ['fileDialog','confirmDialog','aboutDialog'])if($(id).open){$(id).close();return true;}if(!$('drawer').hidden||!$('menu').hidden){closePanels();return true;}saveCurrent();return false;};
document.addEventListener('visibilitychange',()=>{if(document.hidden)saveCurrent();});
window.addEventListener('resize',()=>{document.documentElement.style.setProperty('--terminal','30%');editor.refresh();});
if(native){const files=renderFiles();const last=localStorage.getItem('activeFile');if(files.length)openFile(files.includes(last)?last:files[0]);else updateLabels();}
else {updateLabels();toast('The Android bridge is unavailable. Open this editor in the APK.');}
