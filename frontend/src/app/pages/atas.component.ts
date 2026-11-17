import {CriadorDocumentoComponent} from './criador-documento.component';
import {Component,OnInit,OnDestroy,NgZone,ChangeDetectorRef} from '@angular/core';import {firstValueFrom,from,timeout} from 'rxjs';import {FormsModule} from '@angular/forms';import {ApiService} from '../services/api.service';import {AnexosService} from '../services/anexos.service';@Component({standalone:true,imports:[FormsModule,CriadorDocumentoComponent],template:`@if(criador){<app-criador-documento (fechar)="criador=false" (salvo)="criador=false;listar()"/>}<section><div class="title"><div><h1>Atas e Atividades</h1><p>Registros acadêmicos e revisão textual</p></div><div><button class="secondary" (click)="criador=true">Criar documento</button><button (click)="novo=true">Novo registro</button></div></div>@if(erroAnalise){<p class="error" role="alert">{{erroAnalise}}</p>}@if(sincronizacaoDrive){<p role="status">{{sincronizacaoDrive}}</p>}@if(anexos.mensagem){<p role="status">{{anexos.mensagem}}</p>}<div class="cards">@for(r of registros;track r.id){<article><span class="badge">{{r.tipo}}</span><small>{{r.data}}</small><h2>{{r.titulo}}</h2><p>{{r.descricao}}</p>@if(r.arquivoNome){<p><small>{{r.arquivoNome}}</small></p><button class="secondary" (click)="baixar(r)">Baixar Word / anexo</button>}<label class="button">{{r.arquivoNome?'Substituir anexo':'Anexar arquivo'}}<input hidden type="file" (change)="anexar(r,$event)"></label><button class="secondary" [disabled]="analisandoId !== null" (click)="analisar(r)">{{analisandoId === r.id?'Analisando documento... Pode levar alguns minutos.':'Analisar Word com IA'}}</button></article>}@empty{<div class="empty">Nenhum registro cadastrado.</div>}</div>@if(novo){<div class="modal"><form (ngSubmit)="salvar()"><h2>Novo registro</h2><label>Tipo<select name="tipo" [(ngModel)]="form.tipo"><option>ATA</option><option>ATIVIDADE</option></select></label><label>Título<input name="titulo" [(ngModel)]="form.titulo" required></label><label>Descrição<textarea name="descricao" [(ngModel)]="form.descricao" required></textarea></label><label>Data<input type="date" name="data" [(ngModel)]="form.data" required></label><label>Arquivo<input type="file" (change)="arquivo=$any($event.target).files[0]"></label><div class="actions"><button type="button" class="secondary" (click)="novo=false">Cancelar</button><button>Salvar</button></div></form></div>}@if(resultado){<div class="modal"><div class="dialog analysis-result" role="dialog" aria-modal="true" aria-label="Resultado da análise"><h2>Resultado da análise</h2><p>{{documentoAnalisado}}</p>@if(resultado.caracteresAnalisados){<p>{{resultado.caracteresAnalisados}} caracteres analisados</p>}<h3>Português</h3><ul>@for(x of resultado.errosPortugues;track x){<li>{{x}}</li>}</ul><h3>Gramática</h3><ul>@for(x of resultado.errosGramaticais;track x){<li>{{x}}</li>}</ul><h3>Sugestões</h3><ul>@for(x of resultado.sugestoes;track x){<li>{{x}}</li>}</ul><p><small>O arquivo original não foi alterado.</small></p>@if(erroCorrecao){<p class="error" role="alert">{{erroCorrecao}}</p>}@if(copiaGerada){<p role="status">{{copiaGerada}}</p>}<div class="actions"><button [disabled]="corrigindo || !arquivoAnalisado" (click)="corrigir()">{{corrigindo ? 'Corrigindo documento...' : 'Corrigir Word com IA'}}</button><button class="secondary" [disabled]="corrigindo" (click)="resultado=null">Fechar</button></div></div></div>}</section>`})export class AtasComponent implements OnInit,OnDestroy{sincronizacaoDrive='';private timerDrive?:ReturnType<typeof setInterval>;
ngOnDestroy(){if(this.timerDrive)clearInterval(this.timerDrive)}
private consultarDrive(){this.api.get<{mensagem:string}>('/atas-atividades/sincronizacao').subscribe({next:r=>{this.sincronizacaoDrive=r.mensagem;this.zone.run(()=>this.view.markForCheck())},error:()=>{this.sincronizacaoDrive='Não foi possível consultar o envio ao Drive.';this.zone.run(()=>this.view.markForCheck())}})}
criador=false;registros:any[]=[];novo=false;form:any={tipo:'ATA',data:new Date().toISOString().slice(0,10)};arquivo?:File;resultado:any;documentoAnalisado='';analisandoId:number|null=null;erroAnalise='';arquivoAnalisado?:File;corrigindo=false;erroCorrecao='';copiaGerada='';constructor(private api:ApiService,public anexos:AnexosService,private zone:NgZone,private view:ChangeDetectorRef){}ngOnInit(){this.consultarDrive();this.timerDrive=setInterval(()=>this.consultarDrive(),10000);this.listar();void this.anexos.sincronizarLocais().catch(()=>{this.anexos.mensagem="Não foi possível enviar os anexos ao servidor. Abra esta aba novamente para tentar de novo.";}).finally(()=>this.zone.run(()=>this.view.markForCheck()))}listar(){this.api.get<any[]>('/atas-atividades').subscribe(x=>this.registros=x)}salvar(){const d=new FormData();d.append('dados',new Blob([JSON.stringify(this.form)],{type:'application/json'}));if(this.arquivo)d.append('arquivo',this.arquivo);this.api.post('/atas-atividades',d).subscribe({next:()=>this.zone.run(()=>{this.novo=false;this.arquivo=undefined;this.listar();this.view.markForCheck()}),error:()=>alert('Não foi possível salvar o registro e seu anexo. Confira a conexão com o servidor.')})}async baixar(r:any){try{const arquivo=await this.anexos.obter(r.id);if(!arquivo){alert('Arquivo não encontrado neste navegador. Anexe o documento novamente.');return;}const url=URL.createObjectURL(arquivo);const link=document.createElement('a');link.href=url;link.download=arquivo.name;document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);}catch{alert('Não foi possível abrir o anexo.');}}async anexar(r:any,e:Event){const input=e.target as HTMLInputElement;const arquivo=input.files?.[0];if(!arquivo)return;try{await this.anexos.salvar(r.id,arquivo);this.api.put('/atas-atividades/'+r.id,{arquivoNome:arquivo.name}).subscribe({next:()=>this.listar(),error:()=>alert('Não foi possível atualizar o anexo.')});}catch{alert('Não foi possível salvar o anexo neste navegador.');}finally{input.value='';}}async analisar(r:any){
 if(this.analisandoId !== null)return;
 this.analisandoId=r.id;
 this.erroAnalise='';
 this.resultado=null;
 this.arquivoAnalisado=undefined;this.erroCorrecao='';this.copiaGerada='';
 let etapa='anexo';
 try{
  const arquivo=await firstValueFrom(from(this.anexos.obter(r.id)).pipe(timeout(10000)));
  if(!arquivo)throw new Error('Anexe o documento Word (.docx) deste registro para analisar seu conteúdo.');
  if(!arquivo.name.toLowerCase().endsWith('.docx'))throw new Error('Para analisar o Word, salve o documento como .docx e anexe novamente.');
  if(arquivo.size>100*1024*1024)throw new Error('O arquivo excede o limite de 100 MB.');
  etapa='analise';
  const dados=new FormData();
  dados.append('arquivo',arquivo,arquivo.name);
  // A leitura do IndexedDB pode terminar fora da zona de atualização do Angular.
  const resposta=await this.zone.run(()=>firstValueFrom(this.api.post<any>('/atas-atividades/analisar-documento',dados).pipe(timeout(65000))));
  if(!resposta||!Array.isArray(resposta.errosPortugues)||!Array.isArray(resposta.errosGramaticais)||!Array.isArray(resposta.sugestoes))throw new Error('O servidor retornou uma análise incompleta. Tente novamente.');
  this.resultado=resposta;this.arquivoAnalisado=arquivo;
  this.documentoAnalisado=arquivo.name;
 }catch(e:any){
  this.erroAnalise=e?.name==='TimeoutError'
   ?(etapa==='anexo'?'Não foi possível recuperar o anexo a tempo. Anexe o Word novamente.':'O servidor não retornou a análise em 65 segundos. Tente novamente.')
   :e?.error?.erro||(e?.status===0?'Não foi possível conectar ao servidor de análise.':e?.status===413?'O arquivo excede o limite de 100 MB.':e?.message||'Não foi possível analisar o Word. Tente novamente.');
 }finally{
  this.zone.run(()=>{this.analisandoId=null;this.view.detectChanges();});
 }
}
async corrigir(){
 if(this.corrigindo || !this.arquivoAnalisado || !this.resultado)return;
 this.corrigindo=true;this.erroCorrecao='';this.copiaGerada='';
 try{
  const arquivo=this.arquivoAnalisado;
  const dados=new FormData();dados.append('arquivo',arquivo,arquivo.name);
  dados.append('analise',JSON.stringify(this.resultado));
  const resposta=await this.zone.run(()=>firstValueFrom(this.api.postBlob('/atas-atividades/corrigir-documento',dados).pipe(timeout(125000))));
  const ignoradas=Number(resposta.headers.get('X-Correcoes-Ignoradas'));
  if(resposta.headers.get('X-Correcoes-Aplicadas')==='0'){
   this.copiaGerada=ignoradas
    ? 'Nenhuma sugestão pôde ser aplicada com segurança. Consulte o resultado da análise para revisar o texto.'
    : 'A IA não propôs alterações no documento.';
   return;
  }
  const blob=resposta.body;
  if(!blob?.size)throw new Error('O servidor retornou um arquivo vazio.');
  const url=URL.createObjectURL(blob);const link=document.createElement('a');
  link.href=url;link.download=arquivo.name.replace(/\.docx$/i,'')+'-corrigido.docx';
  document.body.appendChild(link);link.click();link.remove();
  setTimeout(()=>URL.revokeObjectURL(url),60000);
  this.copiaGerada=ignoradas
   ? `Cópia gerada. Sugestões não aplicadas: ${ignoradas}. Revise o documento antes de utilizá-lo.`
   : 'Cópia corrigida gerada. Revise o documento antes de utilizá-lo.';
 }catch(e:any){
  let detalhe=e?.error?.erro;
  if(e?.error instanceof Blob){try{detalhe=JSON.parse(await e.error.text()).erro;}catch{}}
  this.erroCorrecao=detalhe||(e?.name==='TimeoutError'?'A IA não retornou a correção a tempo. Tente novamente.':e?.status===404?'Reinicie o backend para habilitar a correção de Word.':e?.status===0?'Não foi possível conectar ao servidor de correção.':e?.message||'Não foi possível corrigir o Word.');
 }finally{
  this.zone.run(()=>{this.corrigindo=false;this.view.detectChanges();});
 }
}}
