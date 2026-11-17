import {Component,OnInit,OnDestroy,ChangeDetectorRef,NgZone} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpClient} from '@angular/common/http';
import {firstValueFrom,timeout} from 'rxjs';
import {ApiService} from '../services/api.service';

interface Item{id:string;nome:string;paiId:string|null;pasta:boolean;tamanho:number}
@Component({standalone:true,imports:[FormsModule],styles:[`
.toolbar{display:flex;gap:10px;flex-wrap:wrap}
.breadcrumbs{display:flex;flex-direction:row;justify-content:flex-start;align-items:center;gap:8px;flex-wrap:nowrap;overflow-x:auto;max-width:100%;margin:0 0 18px;padding:0 0 4px}
.breadcrumbs .link{flex:0 0 auto;color:#24334b;font-size:18px;padding:6px 0;white-space:nowrap}
.breadcrumbs .link:hover{color:#1769c2;text-decoration:underline}
.breadcrumbs .link[aria-current="page"]{font-weight:600}
.breadcrumbs span{flex:0 0 auto;color:#718096;font-size:24px;padding:0 6px}
.dropzone{border:2px dashed #bdc9d7;border-radius:12px;padding:22px;text-align:center;margin-bottom:22px;background:#fff}
.dropzone.dragging{border-color:#1769c2;background:#e8f1fb}
.files{grid-template-columns:repeat(auto-fill,minmax(min(100%,220px),1fr))}
.file{min-width:0;overflow-wrap:anywhere}
.folder-open{background:transparent;color:inherit;text-align:left;padding:0;width:100%;display:flex;gap:12px;align-items:center}
.folder-icon{font-size:28px;line-height:1;flex-shrink:0}

.notice{padding:12px;background:#e8f1fb;border-radius:8px;margin-bottom:15px;white-space:pre-wrap}
button:disabled{opacity:.6}
`],template:`
<section>
 <div class="title"><div><h1>Meus documentos</h1><p>Pastas e arquivos da sua conta</p></div>
 <div class="toolbar"><button class="secondary" [disabled]="ocupado" (click)="novo=true;nomePasta=''">Nova pasta</button>
 <button [disabled]="ocupado" (click)="seletor.click()">Enviar arquivos</button>
 <input #seletor hidden type="file" multiple (change)="selecionar($event)"></div></div>
 <nav class="breadcrumbs" aria-label="Caminho da pasta">
 <button class="link" [attr.aria-current]="caminho.length===0?'page':null" [disabled]="ocupado" (click)="navegar(-1)">Meus documentos</button>
 @for(p of caminho;track p.id;let i=$index){<span aria-hidden="true">›</span><button class="link" [attr.aria-current]="i===caminho.length-1?'page':null" [disabled]="ocupado" (click)="navegar(i)">{{p.nome}}</button>}
 </nav>
 @if(sincronizacao){<p class="notice" role="status">{{sincronizacao}}</p>}@if(mensagem){<p class="notice" role="status">{{mensagem}}</p>}
 <div class="dropzone" [class.dragging]="arrastando" (dragover)="arrastar($event)" (dragleave)="arrastando=false" (drop)="soltar($event)">
 {{ocupado?'Aguarde a operação terminar…':'Arraste arquivos aqui ou use Enviar arquivos'}}
 <small style="display:block">Até 100 MB por arquivo · Destino: {{atual?.nome||'Meus documentos'}}</small>
 </div>
 @if(carregando){<p role="status">Carregando documentos…</p>}
 <div class="files">
 @for(p of dados.pastas;track p.id){<article class="file"><button class="folder-open" [disabled]="ocupado" (click)="abrir(p)"><span class="folder-icon" aria-hidden="true">📁</span><b>{{p.nome}}</b></button><button class="danger-link" [disabled]="ocupado" (click)="excluir(p)">Excluir pasta</button></article>}
 @for(a of dados.arquivos;track a.id){<article class="file"><b>{{a.nome}}</b><small>{{formatar(a.tamanho)}}</small><div><button class="link" [disabled]="ocupado" (click)="baixar(a)">Baixar</button><button class="danger-link" [disabled]="ocupado" (click)="excluir(a)">Excluir</button></div></article>}
 @if(!carregando&&!dados.pastas.length&&!dados.arquivos.length){<div class="empty">Esta pasta está vazia. Crie uma pasta ou envie arquivos.</div>}
 </div>
 @if(novo){<div class="modal"><form (ngSubmit)="criarPasta()"><h2>Nova pasta</h2><p>Dentro de {{atual?.nome||'Meus documentos'}}</p><label>Nome<input name="nome" [(ngModel)]="nomePasta" maxlength="255" required autofocus></label><div class="actions"><button type="button" class="secondary" [disabled]="ocupado" (click)="novo=false">Cancelar</button><button [disabled]="ocupado||!nomePasta.trim()">Criar</button></div></form></div>}
</section>`})
export class DocumentosComponent implements OnInit,OnDestroy {
 dados:{pastas:Item[];arquivos:Item[]}={pastas:[],arquivos:[]};
 caminho:Item[]=[];ocupado=false;carregando=false;arrastando=false;novo=false;nomePasta='';mensagem='';
 constructor(private api:ApiService,private http:HttpClient,private zone:NgZone,private view:ChangeDetectorRef){}
 get atual(){return this.caminho.at(-1)}
 sincronizacao='';
 private timer?:ReturnType<typeof setInterval>;
 ngOnInit(){void this.carregar();this.consultarSincronizacao();this.timer=setInterval(()=>this.consultarSincronizacao(),10000)}
 ngOnDestroy(){if(this.timer)clearInterval(this.timer)}
 private consultarSincronizacao(){
  this.http.get<{mensagem:string}>(this.api.base+'/documentos/sincronizacao',{withCredentials:true}).subscribe({
   next:resposta=>{this.sincronizacao=resposta.mensagem;this.atualizar()},
   error:()=>{this.sincronizacao='Não foi possível consultar o envio ao Drive.';this.atualizar()}
  });
 }
 private atualizar(){this.zone.run(()=>this.view.markForCheck())}
 async carregar(){
  this.carregando=true;
  try{this.dados=await firstValueFrom(this.api.get<typeof this.dados>('/documentos'+(this.atual?'?pastaId='+this.atual.id:'')).pipe(timeout(15000)))}
  catch(e){this.dados={pastas:[],arquivos:[]};this.erro(e)}
  finally{this.carregando=false;this.atualizar()}
 }
 async abrir(p:Item){if(this.ocupado||this.carregando)return;this.caminho.push(p);await this.carregar()}
 async navegar(indice:number){if(this.ocupado||this.carregando)return;this.caminho=this.caminho.slice(0,indice+1);await this.carregar()}
 async criarPasta(){
  if(this.ocupado||!this.nomePasta.trim())return;
  this.ocupado=true;this.mensagem='';
  try{await firstValueFrom(this.api.post('/documentos/pastas',{nome:this.nomePasta.trim(),paiId:this.atual?.id??null}).pipe(timeout(15000)));this.novo=false;await this.carregar()}
  catch(e){this.erro(e);this.novo=false}
  finally{this.ocupado=false;this.atualizar()}
 }
 selecionar(e:Event){const input=e.target as HTMLInputElement;const arquivos=Array.from(input.files??[]);input.value='';void this.enviar(arquivos)}
 arrastar(e:DragEvent){e.preventDefault();if(!this.ocupado)this.arrastando=true}
 soltar(e:DragEvent){e.preventDefault();this.arrastando=false;void this.enviar(Array.from(e.dataTransfer?.files??[]))}
 async enviar(arquivos:File[]){
  if(this.ocupado||this.carregando||!arquivos.length)return;
  this.ocupado=true;let enviados=0;const falhas:string[]=[];const pastaId=this.atual?.id;
  try{
   for(const arquivo of arquivos){
    if(arquivo.size>100*1024*1024){falhas.push(arquivo.name+' excede 100 MB.');continue}
    this.mensagem='Enviando '+arquivo.name+'…';this.atualizar();
    const dados=new FormData();dados.append('arquivo',arquivo);if(pastaId)dados.append('pastaId',pastaId);
    try{await firstValueFrom(this.api.post('/documentos/arquivos',dados));enviados++}
    catch(e){falhas.push(arquivo.name+' — '+this.descreverErro(e))}
   }
   await this.carregar();
   this.mensagem=enviados+' arquivo(s) enviado(s).'+(falhas.length?'\n'+falhas.join('\n'):'');
  }finally{this.ocupado=false;this.atualizar()}
 }
 async baixar(item:Item){
  this.ocupado=true;
  try{
   const blob=await firstValueFrom(this.http.get(this.api.base+'/documentos/arquivos/'+item.id,{responseType:'blob',withCredentials:true}));
   const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=item.nome;link.click();setTimeout(()=>URL.revokeObjectURL(url),60000);
  }catch(e){this.erro(e)}finally{this.ocupado=false;this.atualizar()}
 }
 async excluir(item:Item){
  const aviso=(item.pasta?'Excluir a pasta "'+item.nome+'" e todos os arquivos e subpastas dentro dela?':'Excluir '+item.nome+'?')+' O conteúdo sincronizado também será enviado à lixeira do Google Drive.';
  if(this.ocupado||!confirm(aviso))return;this.ocupado=true;
  try{await firstValueFrom(this.api.delete('/documentos/'+(item.pasta?'pastas/':'arquivos/')+item.id));this.mensagem='Excluído do projeto. A remoção no Google Drive está sendo processada.';this.consultarSincronizacao();await this.carregar()}
  catch(e){this.erro(e)}finally{this.ocupado=false;this.atualizar()}
 }
 private descreverErro(e:any){return e.status===401?'Entre novamente para acessar seus documentos.':e.error?.erro||'Não foi possível concluir a operação.'}
 private erro(e:unknown){this.mensagem=this.descreverErro(e)}
 formatar(n:number){return n<1024?n+' B':n<1048576?(n/1024).toFixed(1)+' KB':(n/1048576).toFixed(1)+' MB'}
}
