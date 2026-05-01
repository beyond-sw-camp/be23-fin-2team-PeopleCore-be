package com.peoplecore.company.service;

import com.peoplecore.attendance.dto.CompanyAllowedIpReqDto;
import com.peoplecore.attendance.dto.CompanyAllowedIpResDto;
import com.peoplecore.attendance.entity.CompanyAllowedIp;
import com.peoplecore.attendance.repository.CompanyAllowedIpRepository;
import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
@Slf4j
public class CompanyAllowedIpService {
    private final CompanyAllowedIpRepository companyAllowedIpRepository;
    private final CompanyRepository companyRepository;

    public CompanyAllowedIpService(CompanyAllowedIpRepository companyAllowedIpRepository, CompanyRepository companyRepository) {
        this.companyAllowedIpRepository = companyAllowedIpRepository;

        this.companyRepository = companyRepository;
    }

    /* 허용 IP 등록
     * 입력을 CIDR로 정규화 -> 회사 내 중복 검사 -> 형식 불량시 오류코드  */
    @Transactional
    public CompanyAllowedIpResDto create(UUID companyId, CompanyAllowedIpReqDto dto) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        String normalized = normalizeCidr(dto.getIpCidr());
        if (companyAllowedIpRepository.existsByCompany_CompanyIdAndIpCidr(companyId, normalized)) {
            throw new CustomException(ErrorCode.ALLOWED_IP_DUPLICATE);
        }
        CompanyAllowedIp ip = CompanyAllowedIp.builder()
                .company(company)
                .ipCidr(normalized)
                .label(dto.getLabel())
                .isActive(dto.getIsActive() == null ? Boolean.TRUE : dto.getIsActive())
                .build();
        return CompanyAllowedIpResDto.fromEntity(companyAllowedIpRepository.save(ip));
    }

    /*회사별 전체 허용 IP 목록 (관리 화면용 - 활성/ 비활성 모두) */
    public List<CompanyAllowedIpResDto> list(UUID companyId) {
        return companyAllowedIpRepository.findByCompany_CompanyIdOrderByIdAsc(companyId).stream().map(CompanyAllowedIpResDto::fromEntity).toList();
    }

    /*수정 -> 대역/라벨/ 활성 일괄 갱신
     * 타회사 리소스 접근 차단 위해 (id,companyId) 조회
     * CIDR 변경시 중복 검사 (자기 자신 제외) 수행.*/
    @Transactional
    public CompanyAllowedIpResDto update(UUID companyId, Long id, CompanyAllowedIpReqDto dto) {
        CompanyAllowedIp ip = companyAllowedIpRepository.findByIdAndCompany_CompanyId(id, companyId).orElseThrow(() -> new CustomException(ErrorCode.ALLOWED_IP_NOT_FOUND));

        String normalized = normalizeCidr(dto.getIpCidr());

        if (!ip.getIpCidr().equals(normalized) && companyAllowedIpRepository.existsByCompany_CompanyIdAndIpCidr(companyId, normalized)) {
            throw new CustomException(ErrorCode.ALLOWED_IP_DUPLICATE);
        }

        ip.update(normalized, dto.getLabel(), dto.getIsActive());
        return CompanyAllowedIpResDto.fromEntity(ip);
    }

    /*활성/비활성 토클 -> 별도 patch 엔드포인트용 */
    @Transactional
    public CompanyAllowedIpResDto toggle(UUID companyId, Long id) {
        CompanyAllowedIp ip = companyAllowedIpRepository.findByIdAndCompany_CompanyId(id, companyId).orElseThrow(() -> new CustomException(ErrorCode.ALLOWED_IP_NOT_FOUND));

        ip.toggleActive();
        return CompanyAllowedIpResDto.fromEntity(ip);
    }

    /* 허용 IP 삭제 (hard Delete) 이력이 필요해지면 soft delete 전환 고려 */
    @Transactional
    public void delete(UUID companyId, Long id) {
        CompanyAllowedIp ip = companyAllowedIpRepository.findByIdAndCompany_CompanyId(id, companyId).orElseThrow(() -> new CustomException(ErrorCode.ALLOWED_IP_NOT_FOUND));
        companyAllowedIpRepository.delete(ip);
    }

    /* 출퇴근 체크인/아웃 IP 정책 검증
     * 활성 IP 0건  → 정책 미적용으로 보고 모든 IP 허용 (회사 초기 셋업 편의)
     * 활성 IP 1건+ → 등록된 활성 대역에 포함된 IP만 허용
     * @return true: 허용, false: 차단 */
    public boolean isAllowed(UUID companyId, String clientIp) {
        /* 활성 IP 목록 먼저 조회 → 0건이면 정책 미적용으로 즉시 통과 */
        List<CompanyAllowedIp> activeCidrs = companyAllowedIpRepository.findByCompany_CompanyIdAndIsActiveTrue(companyId);
        if (activeCidrs.isEmpty()) return true;

        /* 활성 IP 등록된 회사인데 clientIp 가 비정상이면 차단 */
        if (clientIp == null || clientIp.isBlank()) return false;
        byte[] clientBytes = parseIp(clientIp.trim());
        if (clientBytes == null) return false;

        /* 등록된 활성 대역 중 하나라도 포함하면 허용 */
        for (CompanyAllowedIp ip : activeCidrs) {
            if (cidrContains(ip.getIpCidr(), clientBytes)) return true;
        }
        return false;
    }





    /* CIDR 유틸 (외부 의존겅 없이 InetAddress 기반) */

    /*입력값을 CIDR로 정규화 */
    private String normalizeCidr(String input) {
        /* null 이면 바로 예외, NPE(nullPointException) 방어 */
        if (input == null) throw new CustomException(ErrorCode.INVALID_CIDR_FORMAT);

        /*앞뒤 공백 제거 */
        String raw = input.trim();

        /*ip부분과 prefix(슬래쉬 뒤 숫자) 저장할 변수 선언*/
        String ipPart;
        int prefix;

        /* 문자의 위치 찾기, 없으면 -1 반환*/
        int slash = raw.indexOf('/');

        /* 슬래시 없음 == 단일 IP 입력.prefix를 32로(IP 1개만 허용) 간주*/
        if (slash < 0) {
            ipPart = raw;
            prefix = 32;
        } else {
            /*슬래시 있음. 슬래시 앞부분을 IP로 분리*/
            ipPart = raw.substring(0, slash);
            /* 슬래시 뒷 부분을 숫자로 변환 */
            try {
                prefix = Integer.parseInt(raw.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new CustomException(ErrorCode.INVALID_CIDR_FORMAT);
            }
        }
        /* IP 문자열을 바이트 4개짜리 배열로 변환 ex) "192.168.0.1" -> [192.168.0.1]*/
        byte[] bytes = parseIp(ipPart);
        /*IPV4만 우선 지원 (prefix 0~ 32)*/
        /*검증 4가지
         * byte == null -> IP 파싱 실패
         * bytes.length != 4 -> IPV4 아님
         * prefix < 0 || >32 prefix 범위 벗어남*/
        if (bytes == null || bytes.length != 4 || prefix < 0 || prefix > 32) {
            throw new CustomException(ErrorCode.INVALID_CIDR_FORMAT);
        }

        /* 정규화된 형태로 합쳐서 반환 */
        return ipPart + "/" + prefix;
    }

    /*문자열 IP-> byte[],  파싱 실패 시 null */
    private byte[] parseIp(String ip) {
        try {
            /*java 표준 api 문자열, ip를 InetAddress 객체로 만들고 .getAdress()로 바이트 배열 꺼냄*/
            return InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /*cidr이 clientBytes 를 포함하는지 검사 */
    /* 클라이언트 Ip가 대역에 속하나
     * 대역 문자열과 접속자 IP 바이트를 받음 */
    private boolean cidrContains(String cidr, byte[] clientBytes) {

        /*슬래시 위치 찾고 없으면 잘못된 Cidr이니 false*/
        int slash = cidr.indexOf('/');
        if (slash < 0) return false;

        /* 슬래시 앞(네트워크IP)을 바이트 배열로 */
        byte[] net = parseIp(cidr.substring(0, slash));

        /* 슬래시 뒤(prefix)를 숫자로 실패시 false*/
        int prefix;
        try {
            prefix = Integer.parseInt(cidr.substring(slash + 1));
        } catch (NumberFormatException e) {
            return false;
        }

        /* IP 파실 실패했거나, 한쪽은 IPV4 한쪽은 IPV6처럼 바이트 길이 다르면 비교 불가 */
        if (net == null || net.length != clientBytes.length) return false;

        /* byte 4개를 int 1ro(32비트)로 합침 -> 비트연산자 이래야 비트 연산 가능 */
        int netInt = ByteBuffer.wrap(net).getInt();
        int cliInt = ByteBuffer.wrap(clientBytes).getInt();

        /*마스크 생성 -> prefix = 0 이면 전체 허용(mask 0) , prefix = 32이면 완전 일치*/
        /* 그외 ->  0xFFFFFFFF(32비트 전부 1)을 왼쪽으로 (32-prefix) 칸 shift
         * prefix = 24 -> 왼쪽 8칸 밀림 -> 앞 24비트만 1,나머지 8비트 0 */
        int mask = prefix == 0 ? 0 : 0xFFFFFFFF << (32 - prefix);

        /*netInt&mask : 대역 IP에서 네트워크 부분만 추출
         * client & mask : 접속자 Ip에서 네트워크 부분만 추출 같으면 같은 대역 -> true*/
        return (netInt & mask) == (cliInt & mask);
    }
}

