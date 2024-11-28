import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { GqlLogStatement } from '../../../generated/graphql';
import { ModalService } from '../../services/modal.service';
import {
  CodeEditorModalComponent,
  CodeEditorModalComponentProps,
} from '../../modals/code-editor-modal/code-editor-modal.component';
import { IonButton } from '@ionic/angular/standalone';

@Component({
  selector: 'app-console-button',
  templateUrl: './console-button.component.html',
  styleUrls: ['./console-button.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IonButton, CodeEditorModalComponent],
  standalone: true,
})
export class ConsoleButtonComponent {
  @Input()
  logs: GqlLogStatement[];

  constructor(private readonly modalService: ModalService) {}

  openModal() {
    const props: CodeEditorModalComponentProps = {
      title: 'Log Output',
      contentType: 'text',
      readOnly: true,
      controls: false,
      text: stringifyLogStatement(this.logs),
    };
    return this.modalService.openCodeEditorModal(props);
  }
}

const stringifyLogStatement = (lsl: GqlLogStatement[]): string =>
  lsl
    .map((ls) => `${new Date(ls.time).toLocaleTimeString()}\t ${ls.message}`)
    .join('\n');
