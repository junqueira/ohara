import React from 'react';
import styled from 'styled-components';
import toastr from 'toastr';
import DocumentTitle from 'react-document-title';
import { Prompt } from 'react-router-dom';

import { AppWrapper } from '../common/Layout';
import { Input, Button, FormGroup, Label } from '../common/Form';
import { primaryBtn, cancelBtn } from '../../theme/btnTheme';
import { lighterGray } from '../../theme/variables';
import { CONFIGURATION } from '../../constants/documentTitles';
import {
  validateHdfs,
  saveHdfs,
  fetchHdfs,
} from '../../apis/configurationApis';
import * as _ from '../../utils/helpers';
import * as MESSAGES from '../../constants/messages';

const FormInner = styled.div`
  padding: 45px 30px;
`;

const Actions = styled.div`
  display: flex;
  padding: 20px;
  border-top: 1px solid ${lighterGray};
`;

const ActionGroup = styled.div`
  margin-left: auto;
`;

const CancelBtn = styled(Button)`
  margin-right: 10px;
`;

class ConfigurationPage extends React.Component {
  state = {
    connectionName: '',
    connectionUrl: '',
    isFormDirty: false,
    isWorking: false,
    isValidConnection: false,
  };

  componentDidMount() {
    this.fetchData();
  }

  fetchData = async () => {
    const res = await fetchHdfs();

    const _result = _.get(res, 'data.result');

    if (_result && _result.length > 0) {
      const target = res.data.result.reduce(
        (prev, curr) => (prev.lastModified > curr.lastModified ? prev : curr),
      );
      const { name: connectionName, uri: connectionUrl } = target;
      this.setState({ connectionName, connectionUrl });
    }
  };

  handleChange = ({ target: { value, id } }) => {
    this.setState({ [id]: value, isFormDirty: true });
  };

  handleCancel = e => {
    e.preventDefault();
    this.props.history.goBack();
  };

  handleSave = async () => {
    const { connectionName: name, connectionUrl: uri } = this.state;
    const res = await saveHdfs({ name, uri });

    const _res = _.get(res, 'data.isSuccess', false);

    if (_.isDefined(_res)) {
      toastr.success(MESSAGES.CONFIG_SAVE_SUCCESS);
    }
  };

  handleTest = async e => {
    e.preventDefault();
    const { connectionUrl: uri } = this.state;
    this.updateIsWorking(true);
    const res = await validateHdfs({ uri });
    this.updateIsWorking(false);

    const _res = _.get(res, 'data.isSuccess', false);

    if (_res) {
      toastr.success(MESSAGES.TEST_SUCCESS);
      this.handleSave();
    }
  };

  updateIsWorking = update => {
    this.setState({ isWorking: update });
  };

  render() {
    const {
      isFormDirty,
      connectionName,
      connectionUrl,
      isWorking,
    } = this.state;
    return (
      <DocumentTitle title={CONFIGURATION}>
        <AppWrapper title="Configuration">
          <Prompt
            when={isFormDirty || isWorking}
            message={MESSAGES.LEAVE_WITHOUT_SAVE}
          />
          <form>
            <FormInner>
              <FormGroup>
                <Label>Name</Label>
                <Input
                  id="connectionName"
                  width="250px"
                  placeholder="Connection name"
                  value={connectionName}
                  data-testid="connection-name-input"
                  handleChange={this.handleChange}
                />
              </FormGroup>

              <FormGroup>
                <Label>HDFS connection URL</Label>
                <Input
                  id="connectionUrl"
                  width="250px"
                  placeholder="http://localhost:5050"
                  value={connectionUrl}
                  data-testid="connection-url-input"
                  handleChange={this.handleChange}
                />
              </FormGroup>
            </FormInner>

            <Actions>
              <ActionGroup>
                <CancelBtn
                  text="Cancel"
                  theme={cancelBtn}
                  handleClick={this.handleCancel}
                />
                <Button
                  theme={primaryBtn}
                  text="Test connection"
                  isWorking={isWorking}
                  data-testid="test-connection-btn"
                  handleClick={this.handleTest}
                />
              </ActionGroup>
            </Actions>
          </form>
        </AppWrapper>
      </DocumentTitle>
    );
  }
}

export default ConfigurationPage;